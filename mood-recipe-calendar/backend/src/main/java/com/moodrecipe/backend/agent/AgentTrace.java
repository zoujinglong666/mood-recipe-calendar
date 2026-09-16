package com.moodrecipe.backend.agent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 一次智能体运行的可观测轨迹：每一步做了什么、耗时多久、成功与否。 */
public final class AgentTrace {

    public record Step(String kind, String name, long latencyMs, String summary, boolean ok) {}

    private final String traceId;
    private final List<Step> steps = Collections.synchronizedList(new ArrayList<>());

    public AgentTrace(String traceId) {
        this.traceId = traceId == null ? "unknown" : traceId;
    }

    public String traceId() {
        return traceId;
    }

    public void record(String kind, String name, long latencyMs, String summary, boolean ok) {
        steps.add(new Step(kind, name, latencyMs, trim(summary), ok));
    }

    public List<Step> steps() {
        return List.copyOf(steps);
    }

    public boolean hasFailure() {
        return steps.stream().anyMatch(step -> !step.ok());
    }

    private static String trim(String value) {
        if (value == null) return "";
        return value.length() <= 300 ? value : value.substring(0, 300) + "…";
    }
}
