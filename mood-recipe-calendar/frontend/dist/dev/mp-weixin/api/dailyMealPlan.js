"use strict";
const api_request = require("./request.js");
function fetchDailyMealPlan(date) {
  return api_request.get("/daily-meal-plan", { date }).then(normalizePlan);
}
function replaceDailyMealPlanMeal(mealIndex, date) {
  return api_request.post(`/daily-meal-plan/${mealIndex}/replace`, void 0, void 0, { date: date || "" }).then(normalizePlan);
}
function normalizePlan(plan) {
  if (!plan)
    return plan;
  return {
    ...plan,
    meals: (plan.meals || []).map((m) => ({ ...m, image: api_request.resolveAssetUrl(m.image) }))
  };
}
exports.fetchDailyMealPlan = fetchDailyMealPlan;
exports.replaceDailyMealPlanMeal = replaceDailyMealPlanMeal;
