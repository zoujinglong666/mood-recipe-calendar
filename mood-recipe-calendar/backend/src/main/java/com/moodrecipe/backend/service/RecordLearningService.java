package com.moodrecipe.backend.service;

import com.moodrecipe.backend.agent.AgentMemoryStore;
import com.moodrecipe.backend.entity.RecipeInteraction;
import com.moodrecipe.backend.model.LearningReceipt;
import com.moodrecipe.backend.model.LearningReceiptItem;
import com.moodrecipe.backend.repository.RecipeInteractionRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class RecordLearningService {
    public static final String KEY_AVOID_LEFTOVER = "preference.avoidLeftover";

    private final RecipeInteractionRepository interactions;
    private final RecommendationExposureService exposures;
    private final AgentMemoryStore memories;

    public RecordLearningService(RecipeInteractionRepository interactions,
                                 RecommendationExposureService exposures,
                                 AgentMemoryStore memories) {
        this.interactions = interactions;
        this.exposures = exposures;
        this.memories = memories;
    }

    public LearningReceipt learn(String openid, String recipeId, String exposureId,
                                 boolean liked, boolean tooHard, boolean leftover) {
        if (!memories.personalizationEnabled(openid)) return LearningReceipt.savedOnly();

        List<LearningReceiptItem> learned = new ArrayList<>();
        Long persistentRecipeId = parseRecipeId(recipeId);
        boolean madeWritten = recordAction(openid, persistentRecipeId, "MADE");
        boolean madeExposureWritten = recordExposure(openid, exposureId, "MADE");
        if (madeWritten || madeExposureWritten) {
            learned.add(new LearningReceiptItem("MADE", "记住你做过这道菜", null));
        }
        if (liked) {
            boolean likedWritten = recordAction(openid, persistentRecipeId, "LIKE");
            boolean likedExposureWritten = recordExposure(openid, exposureId, "LIKE");
            if (likedWritten || likedExposureWritten) {
                learned.add(new LearningReceiptItem("LIKED", "以后多推荐你喜欢的味道", null));
            }
        }
        if (tooHard) {
            boolean simpleWritten = remember(openid, AgentMemoryStore.KEY_SIMPLE, "true", "用户记录时反馈做起来太难");
            boolean timeWritten = remember(openid, AgentMemoryStore.KEY_MAX_MINUTES, "35", "用户记录时反馈做起来太难");
            if (simpleWritten && timeWritten) {
                learned.add(new LearningReceiptItem("SIMPLE", "下次优先 35 分钟内的简单菜",
                        AgentMemoryStore.KEY_SIMPLE));
            } else if (simpleWritten) {
                learned.add(new LearningReceiptItem("SIMPLE", "下次优先做法简单的菜",
                        AgentMemoryStore.KEY_SIMPLE));
            } else if (timeWritten) {
                learned.add(new LearningReceiptItem("TIME", "下次尽量控制在 35 分钟内",
                        AgentMemoryStore.KEY_MAX_MINUTES));
            }
        }
        if (leftover && remember(openid, KEY_AVOID_LEFTOVER, "true", "用户记录时反馈有剩菜")) {
            learned.add(new LearningReceiptItem("LEFTOVER", "下次会把分量安排得更克制", KEY_AVOID_LEFTOVER));
        }
        return learned.isEmpty() ? LearningReceipt.savedOnly() : LearningReceipt.learned(learned);
    }

    private boolean recordAction(String openid, Long recipeId, String action) {
        if (recipeId == null) return false;
        try {
            if (interactions.existsByOpenidAndRecipeIdAndAction(openid, recipeId, action)) return false;
            RecipeInteraction interaction = new RecipeInteraction();
            interaction.setOpenid(openid);
            interaction.setRecipeId(recipeId);
            interaction.setAction(action);
            interactions.save(interaction);
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private boolean recordExposure(String openid, String exposureId, String action) {
        if (exposureId == null || exposureId.isBlank()) return false;
        try {
            return exposures.feedback(openid, exposureId, action) != null;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private boolean remember(String openid, String key, String value, String evidence) {
        try {
            return memories.remember(new AgentMemoryStore.RememberCommand(openid, key, value, 0.85,
                    AgentMemoryStore.SRC_EXPLICIT, evidence, null)) != null;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private Long parseRecipeId(String recipeId) {
        try {
            return recipeId == null || recipeId.isBlank() ? null : Long.valueOf(recipeId);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
