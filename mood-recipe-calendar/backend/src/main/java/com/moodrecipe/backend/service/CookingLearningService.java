package com.moodrecipe.backend.service;

import com.moodrecipe.backend.entity.CookingLearningEvent;
import com.moodrecipe.backend.repository.CookingLearningEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Service
public class CookingLearningService {
    private static final Set<String> EVENTS = Set.of("COMPLETED", "TOO_HARD", "NOT_COMPLETED", "HELP");
    private final CookingLearningEventRepository repository;

    public CookingLearningService(CookingLearningEventRepository repository) { this.repository = repository; }

    public String teachingLevel(String openid, boolean personalized) {
        if (!personalized) return "GUIDED";
        List<CookingLearningEvent> events = repository.findTop50ByOpenidOrderByCreatedAtDesc(openid);
        long completed = events.stream().filter(item -> "COMPLETED".equals(item.getEventType())).count();
        long struggling = events.stream().filter(item -> "TOO_HARD".equals(item.getEventType())
                || "NOT_COMPLETED".equals(item.getEventType()) || "HELP".equals(item.getEventType())).count();
        if (struggling >= 2) return "BEGINNER";
        if (completed >= 5 && struggling == 0) return "COMPACT";
        return "GUIDED";
    }

    public void record(String openid, Long recipeId, Integer stepIndex, String stepType, String eventType) {
        String normalized = eventType == null ? "" : eventType.trim().toUpperCase();
        if (!EVENTS.contains(normalized)) throw new IllegalArgumentException("反馈类型无效");
        CookingLearningEvent event = new CookingLearningEvent();
        event.setOpenid(openid); event.setRecipeId(recipeId); event.setStepIndex(stepIndex);
        event.setStepType(trim(stepType, 48)); event.setEventType(normalized);
        repository.save(event);
    }

    @Transactional
    public void clear(String openid) { repository.deleteByOpenid(openid); }

    private String trim(String value, int max) {
        if (value == null) return null;
        value = value.trim();
        return value.length() <= max ? value : value.substring(0, max);
    }
}
