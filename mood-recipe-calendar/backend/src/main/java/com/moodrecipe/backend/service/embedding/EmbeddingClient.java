package com.moodrecipe.backend.service.embedding;

import java.util.List;

/** 文本嵌入客户端：把文本映射为定长向量，供向量检索使用。 */
public interface EmbeddingClient {

    /** 是否已配置（有可用的 key / 端点）。 */
    boolean isConfigured();

    /** 批量嵌入，返回与输入顺序一致的向量列表；空文本或无 key 时返回空向量（length=0）。 */
    List<float[]> embed(List<String> texts);

    /** 单条嵌入便捷方法。 */
    default float[] embed(String text) {
        List<float[]> r = embed(java.util.List.of(text));
        return r.isEmpty() ? new float[0] : r.get(0);
    }
}
