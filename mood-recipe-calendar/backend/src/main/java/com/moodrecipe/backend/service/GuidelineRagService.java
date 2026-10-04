package com.moodrecipe.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moodrecipe.backend.entity.GuidelineChunk;
import com.moodrecipe.backend.repository.GuidelineChunkRepository;
import com.moodrecipe.backend.service.embedding.EmbeddingClient;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 《中国居民膳食指南》向量检索服务。
 *
 * 设计取舍：向量存在 DB 的 embedding_json 字段，启动时把已启用块加载进内存做余弦检索。
 * 数千条规模下内存与计算开销极小；若日后上量再迁移到 MySQL 9 的 VECTOR 列 + 向量索引。
 */
@Service
public class GuidelineRagService {

    private static final Logger log = LoggerFactory.getLogger(GuidelineRagService.class);

    private final GuidelineChunkRepository repo;
    private final EmbeddingClient embedding;
    private final ObjectMapper json;

    private volatile List<Entry> index = List.of();
    private final AtomicBoolean ready = new AtomicBoolean(false);

    public GuidelineRagService(GuidelineChunkRepository repo,
                              EmbeddingClient embedding,
                              ObjectMapper json) {
        this.repo = repo;
        this.embedding = embedding;
        this.json = json;
    }

    @PostConstruct
    public void refresh() {
        rebuild();
    }

    /** 重新加载内存向量索引（导入新数据后调用）。 */
    public void rebuild() {
        try {
            List<GuidelineChunk> all = repo.findByEnabledTrue();
            List<Entry> entries = new ArrayList<>();
            for (GuidelineChunk c : all) {
                float[] v = parseVector(c.getEmbeddingJson());
                if (v == null || v.length == 0) continue;
                entries.add(new Entry(c.getId(), c.getPage(), c.getSection(), c.getCategory(),
                        c.getTitle(), c.getContent(), c.getTags(), c.getSourceName(), c.getSourceUrl(), v));
            }
            index = entries;
            ready.set(!entries.isEmpty());
            log.info("指南 RAG 索引已加载 {} 条", entries.size());
        } catch (Exception ex) {
            log.warn("指南 RAG 索引加载失败：{}", ex.toString());
            ready.set(false);
        }
    }

    public boolean isReady() {
        return ready.get() && embedding.isConfigured();
    }

    /** 返回 top-k 命中，按相似度降序；每条含改写后的要点与来源。 */
    public List<Map<String, Object>> search(String query, int k) {
        if (!isReady() || query == null || query.isBlank()) return List.of();
        float[] q = embedding.embed(query);
        if (q == null || q.length == 0) return List.of();
        int topK = Math.max(1, Math.min(k, 10));
        PriorityQueue<Scored> pq = new PriorityQueue<>(Comparator.comparingDouble(s -> s.score));
        for (Entry e : index) {
            pq.add(new Scored(e, cosine(q, e.vector)));
            if (pq.size() > topK) pq.poll();
        }
        List<Scored> sorted = new ArrayList<>(pq);
        sorted.sort((a, b) -> Double.compare(b.score, a.score));
        List<Map<String, Object>> out = new ArrayList<>();
        for (Scored s : sorted) {
            Entry e = s.item;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("page", e.page);
            if (e.section != null) m.put("section", e.section);
            m.put("category", e.category);
            m.put("title", e.title);
            m.put("content", e.content);
            if (e.tags != null && !e.tags.isBlank()) m.put("tags", Arrays.asList(e.tags.split(",")));
            m.put("source", e.sourceName);
            if (e.sourceUrl != null) m.put("sourceUrl", e.sourceUrl);
            m.put("score", Math.round(s.score * 1000.0) / 1000.0);
            out.add(m);
        }
        return out;
    }

    private float[] parseVector(String jsonStr) {
        if (jsonStr == null || jsonStr.isBlank()) return null;
        try {
            JsonNode node = json.readTree(jsonStr);
            if (!node.isArray()) return null;
            float[] a = new float[node.size()];
            for (int i = 0; i < node.size(); i++) a[i] = (float) node.path(i).asDouble();
            return a;
        } catch (Exception ex) {
            return null;
        }
    }

    private static double cosine(float[] a, float[] b) {
        if (a.length != b.length) return 0;
        double dot = 0, na = 0, nb = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            na += a[i] * a[i];
            nb += b[i] * b[i];
        }
        if (na == 0 || nb == 0) return 0;
        return dot / (Math.sqrt(na) * Math.sqrt(nb));
    }

    private record Entry(Long id, Integer page, String section, String category, String title,
                         String content, String tags, String sourceName, String sourceUrl, float[] vector) {
    }

    private record Scored(Entry item, double score) {
    }
}
