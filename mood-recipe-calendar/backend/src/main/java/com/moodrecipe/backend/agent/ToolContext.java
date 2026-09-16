package com.moodrecipe.backend.agent;

import com.fasterxml.jackson.databind.ObjectMapper;

/** 工具执行上下文：只知道是谁、哪条链路、怎么序列化，不持有业务状态。 */
public record ToolContext(String openid, String traceId, ObjectMapper json) {
}
