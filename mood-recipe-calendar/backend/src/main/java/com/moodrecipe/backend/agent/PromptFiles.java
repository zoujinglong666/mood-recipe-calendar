package com.moodrecipe.backend.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 提示词外置加载器（pi 的 AGENTS.md 思想："Change the harness, not your workflow"）。
 *
 * 提示词是马具配置，不该焊死在代码里：
 * - 内置事实源：classpath:/agent/prompts/&lt;name&gt;.md，随 jar 发布；
 * - 可选覆盖：./config/agent-prompts/&lt;name&gt;.md，存在即优先——线上调优提示词不发版；
 * - 热加载：每 60 秒感知一次覆盖文件的修改，改完文件即生效，无需重启（对齐 pi 的 /reload）；
 * - 失败降级：任何读取问题返回空串并记日志，绝不阻断对话主链路。
 */
public final class PromptFiles {

    private static final Logger log = LoggerFactory.getLogger(PromptFiles.class);
    private static final String CLASSPATH_ROOT = "/agent/prompts/";
    private static final Path OVERRIDE_DIR = Path.of("config", "agent-prompts");
    private static final long RELOAD_INTERVAL_MS = 60_000L;

    private record Loaded(long checkedAt, long overrideMtime, String text) {
    }

    private static final Map<String, Loaded> CACHE = new ConcurrentHashMap<>();

    private PromptFiles() {
    }

    /** 读取一段提示词：外置覆盖优先，classpath 内置兜底；60 秒内直接用缓存。 */
    public static String get(String name) {
        long now = System.currentTimeMillis();
        Loaded cached = CACHE.get(name);
        if (cached != null && now - cached.checkedAt() < RELOAD_INTERVAL_MS) {
            return cached.text();
        }
        long overrideMtime = mtimeOf(OVERRIDE_DIR.resolve(name + ".md"));
        if (cached != null && overrideMtime == cached.overrideMtime()) {
            CACHE.put(name, new Loaded(now, cached.overrideMtime(), cached.text()));
            return cached.text();
        }
        String text = overrideMtime >= 0
                ? readOverride(OVERRIDE_DIR.resolve(name + ".md"))
                : readClasspath(name);
        if (text.isBlank()) {
            log.warn("提示词缺失：{}（classpath 与外置覆盖均未读到，相关能力将退化为无指令状态）", name);
        }
        CACHE.put(name, new Loaded(now, overrideMtime, text));
        return text;
    }

    private static String readClasspath(String name) {
        try (InputStream stream = PromptFiles.class.getResourceAsStream(CLASSPATH_ROOT + name + ".md")) {
            if (stream == null) return "";
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8).trim();
        } catch (Exception error) {
            log.warn("读取内置提示词失败：{}（{}）", name, error.getMessage());
            return "";
        }
    }

    private static String readOverride(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8).trim();
        } catch (Exception error) {
            log.warn("读取外置覆盖提示词失败：{}（{}），回退内置版本", file, error.getMessage());
            return "";
        }
    }

    private static long mtimeOf(Path file) {
        try {
            if (!Files.isRegularFile(file)) return -1L;
            return Files.getLastModifiedTime(file).toMillis();
        } catch (Exception error) {
            return -1L;
        }
    }
}
