package com.moodrecipe.backend.service.search;

import java.util.List;

/**
 * 联网搜索的抽象。当前提供 Tavily 实现；未来接 Serper / 博查 只需新增实现类，
 * 上层 {@code WebSearchTool} 无需改动。
 */
public interface SearchClient {

    /** 是否已配置可用（未配 key 时上层直接降级，不发请求）。 */
    boolean available();

    /**
     * 执行一次搜索。
     *
     * @param query      搜索词
     * @param maxResults 期望条数
     * @return 结果列表；失败或不可用时返回空列表（绝不抛异常到调用方）
     */
    List<SearchResult> search(String query, int maxResults);

    /** 单条搜索结果：标题 + 摘要 + 链接。 */
    record SearchResult(String title, String snippet, String url) {}
}
