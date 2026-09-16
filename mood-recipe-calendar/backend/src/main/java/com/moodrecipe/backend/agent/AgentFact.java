package com.moodrecipe.backend.agent;

/** 从一句话里抽出来的结构化事实：值、置信度、是否用户亲口说的、依据是什么。 */
public record AgentFact(String key, String value, double confidence, boolean explicit, String evidence) {

    public static AgentFact explicit(String key, String value, String evidence) {
        return new AgentFact(key, value, 0.85, true, evidence);
    }

    public static AgentFact inferred(String key, String value, String evidence) {
        return new AgentFact(key, value, 0.55, false, evidence);
    }

    public boolean worthRemembering() {
        return key != null && !key.isBlank() && value != null && !value.isBlank() && confidence >= 0.45;
    }

    public String describe() {
        return key + "=" + value + "（" + (explicit ? "明确" : "推断") + "，" + evidence + "）";
    }
}
