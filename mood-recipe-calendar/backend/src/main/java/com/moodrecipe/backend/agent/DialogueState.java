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
                       List<String> degraded,
                       List<Card> cards) {
        public Turn(String reply, String action, AgentState state, Card card,
                    String askReason, List<String> memoryUsed, List<String> conflicts, List<String> degraded) {
            this(reply, action, state, card, askReason, memoryUsed, conflicts, degraded,
                    card == null ? List.of() : List.of(card));
        }
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
                             String mealContext,
                             List<String> requestedIngredients) {

        public static AgentState empty() {
            return new AgentState(null, List.of(), null, null, null, null, null, null, null, false, null, List.of());
        }

        public AgentState(Integer people, List<Integer> cookingDays, Integer dishesPerDay,
                          String healthGoal, String budget, Boolean hasElder, Boolean hasChild,
                          String spiceLevel, String favoriteCuisine, Boolean cuisineConfirmed,
                          String mealContext) {
            this(people, cookingDays, dishesPerDay, healthGoal, budget, hasElder, hasChild,
                    spiceLevel, favoriteCuisine, cuisineConfirmed, mealContext, List.of());
        }

        public AgentState withPeople(Integer value) {
            return copy(value, cookingDays, dishesPerDay, healthGoal, budget, hasElder, hasChild, spiceLevel, favoriteCuisine, cuisineConfirmed, mealContext, requestedIngredients);
        }

        public AgentState withCookingDays(List<Integer> value) {
            return copy(people, value, dishesPerDay, healthGoal, budget, hasElder, hasChild, spiceLevel, favoriteCuisine, cuisineConfirmed, mealContext, requestedIngredients);
        }

        public AgentState withDishesPerDay(Integer value) {
            return copy(people, cookingDays, value, healthGoal, budget, hasElder, hasChild, spiceLevel, favoriteCuisine, cuisineConfirmed, mealContext, requestedIngredients);
        }

        public AgentState withHealthGoal(String value) {
            return copy(people, cookingDays, dishesPerDay, value, budget, hasElder, hasChild, spiceLevel, favoriteCuisine, cuisineConfirmed, mealContext, requestedIngredients);
        }

        public AgentState withBudget(String value) {
            return copy(people, cookingDays, dishesPerDay, healthGoal, value, hasElder, hasChild, spiceLevel, favoriteCuisine, cuisineConfirmed, mealContext, requestedIngredients);
        }

        public AgentState withHasElder(Boolean value) {
            return copy(people, cookingDays, dishesPerDay, healthGoal, budget, value, hasChild, spiceLevel, favoriteCuisine, cuisineConfirmed, mealContext, requestedIngredients);
        }

        public AgentState withHasChild(Boolean value) {
            return copy(people, cookingDays, dishesPerDay, healthGoal, budget, hasElder, value, spiceLevel, favoriteCuisine, cuisineConfirmed, mealContext, requestedIngredients);
        }

        public AgentState withSpiceLevel(String value) {
            return copy(people, cookingDays, dishesPerDay, healthGoal, budget, hasElder, hasChild, value, favoriteCuisine, cuisineConfirmed, mealContext, requestedIngredients);
        }

        public AgentState withFavoriteCuisine(String value) {
            return copy(people, cookingDays, dishesPerDay, healthGoal, budget, hasElder, hasChild, spiceLevel, value, cuisineConfirmed, mealContext, requestedIngredients);
        }

        public AgentState withCuisineConfirmed(Boolean value) {
            return copy(people, cookingDays, dishesPerDay, healthGoal, budget, hasElder, hasChild, spiceLevel, favoriteCuisine, value, mealContext, requestedIngredients);
        }

        public AgentState withHousehold(Boolean elder, Boolean child) {
            return copy(people, cookingDays, dishesPerDay, healthGoal, budget, elder, child, spiceLevel, favoriteCuisine, cuisineConfirmed, mealContext, requestedIngredients);
        }

        public AgentState withMealContext(String value) {
            return copy(people, cookingDays, dishesPerDay, healthGoal, budget, hasElder, hasChild, spiceLevel, favoriteCuisine, cuisineConfirmed, value, requestedIngredients);
        }

        public AgentState withRequestedIngredients(List<String> value) {
            return copy(people, cookingDays, dishesPerDay, healthGoal, budget, hasElder, hasChild,
                    spiceLevel, favoriteCuisine, cuisineConfirmed, mealContext,
                    value == null ? List.of() : List.copyOf(value));
        }

        private static AgentState copy(Integer people, List<Integer> cookingDays, Integer dishesPerDay,
                                       String healthGoal, String budget, Boolean hasElder, Boolean hasChild,
                                       String spiceLevel, String favoriteCuisine, Boolean cuisineConfirmed,
                                       String mealContext, List<String> requestedIngredients) {
            return new AgentState(people, cookingDays, dishesPerDay, healthGoal, budget, hasElder, hasChild,
                    spiceLevel, favoriteCuisine, cuisineConfirmed, mealContext,
                    requestedIngredients == null ? List.of() : List.copyOf(requestedIngredients));
        }
    }
}
