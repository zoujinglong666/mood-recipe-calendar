package com.moodrecipe.backend.agent;

import java.util.List;

/**
 * 备餐对话的共享值类型。
 *
 * 单独成类是为了让智能体（agent 包）与业务服务（service 包）之间只依赖数据结构，
 * 不产生 Spring Bean 的循环依赖，同时让"状态"这一概念有唯一定义处。
 */
public final class DialogueState {

    private DialogueState() {
    }

    /** 一次对话轮次的完整结果：说了什么、要做什么、用了哪些记忆、哪里降级了。 */
    public record Turn(String reply,
                       String action,
                       AgentState state,
                       Card card,
                       String askReason,
                       List<String> memoryUsed,
                       List<String> conflicts,
                       List<String> degraded) {
        public Turn(String reply, String action, AgentState state, Card card) {
            this(reply, action, state, card, "", List.of(), List.of(), List.of());
        }
    }

    public record Option(String label, String value) {}

    public record Card(String type, String title, String description, List<Option> options) {}

    /**
     * 备餐会话状态。全部字段可空，空表示"还不知道"，绝不用默认值冒充用户说过的话。
     */
    public record AgentState(Integer people,
                             List<Integer> cookingDays,
                             Integer dishesPerDay,
                             String healthGoal,
                             String budget,
                             Boolean hasElder,
                             Boolean hasChild,
                             String spiceLevel,
                             String favoriteCuisine,
                             Boolean cuisineConfirmed,
                             String mealContext) {

        public static AgentState empty() {
            return new AgentState(null, List.of(), null, null, null, null, null, null, null, false, null);
        }

        public AgentState withPeople(Integer value) {
            return new AgentState(value, cookingDays, dishesPerDay, healthGoal, budget, hasElder, hasChild, spiceLevel, favoriteCuisine, cuisineConfirmed, mealContext);
        }

        public AgentState withCookingDays(List<Integer> value) {
            return new AgentState(people, value, dishesPerDay, healthGoal, budget, hasElder, hasChild, spiceLevel, favoriteCuisine, cuisineConfirmed, mealContext);
        }

        public AgentState withDishesPerDay(Integer value) {
            return new AgentState(people, cookingDays, value, healthGoal, budget, hasElder, hasChild, spiceLevel, favoriteCuisine, cuisineConfirmed, mealContext);
        }

        public AgentState withHealthGoal(String value) {
            return new AgentState(people, cookingDays, dishesPerDay, value, budget, hasElder, hasChild, spiceLevel, favoriteCuisine, cuisineConfirmed, mealContext);
        }

        public AgentState withBudget(String value) {
            return new AgentState(people, cookingDays, dishesPerDay, healthGoal, value, hasElder, hasChild, spiceLevel, favoriteCuisine, cuisineConfirmed, mealContext);
        }

        public AgentState withHasElder(Boolean value) {
            return new AgentState(people, cookingDays, dishesPerDay, healthGoal, budget, value, hasChild, spiceLevel, favoriteCuisine, cuisineConfirmed, mealContext);
        }

        public AgentState withHasChild(Boolean value) {
            return new AgentState(people, cookingDays, dishesPerDay, healthGoal, budget, hasElder, value, spiceLevel, favoriteCuisine, cuisineConfirmed, mealContext);
        }

        public AgentState withSpiceLevel(String value) {
            return new AgentState(people, cookingDays, dishesPerDay, healthGoal, budget, hasElder, hasChild, value, favoriteCuisine, cuisineConfirmed, mealContext);
        }

        public AgentState withFavoriteCuisine(String value) {
            return new AgentState(people, cookingDays, dishesPerDay, healthGoal, budget, hasElder, hasChild, spiceLevel, value, cuisineConfirmed, mealContext);
        }

        public AgentState withCuisineConfirmed(Boolean value) {
            return new AgentState(people, cookingDays, dishesPerDay, healthGoal, budget, hasElder, hasChild, spiceLevel, favoriteCuisine, value, mealContext);
        }

        public AgentState withHousehold(Boolean elder, Boolean child) {
            return new AgentState(people, cookingDays, dishesPerDay, healthGoal, budget, elder, child, spiceLevel, favoriteCuisine, cuisineConfirmed, mealContext);
        }

        public AgentState withMealContext(String value) {
            return new AgentState(people, cookingDays, dishesPerDay, healthGoal, budget, hasElder, hasChild, spiceLevel, favoriteCuisine, cuisineConfirmed, value);
        }
    }
}
