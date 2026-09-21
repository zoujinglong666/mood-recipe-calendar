"use strict";
const common_vendor = require("../common/vendor.js");
const api_request = require("./request.js");
function getCurrentPlan() {
  return api_request.get("/weekly-plans/current");
}
function getWeeklyPlan(id) {
  return api_request.get(`/weekly-plans/${id}`);
}
function getWeeklyPlanHistory() {
  return api_request.get("/weekly-plans/history");
}
function generateWeeklyPlan(data) {
  return api_request.post("/weekly-plans/generate", data);
}
function runMealAgentTurn(message, state) {
  return api_request.post("/weekly-plans/agent-turns", { message, state });
}
const WEEKLY_PLAN_TEMPLATE_ID = "h00FlM2Xf_X64sXln5WoYGnbvtJBjasdEraRPjs4NOg";
function requestWeeklyPlanCompletionNotice() {
  return new Promise((resolve) => {
    const requestSubscribeMessage = common_vendor.index.requestSubscribeMessage;
    if (typeof requestSubscribeMessage !== "function") {
      resolve(false);
      return;
    }
    requestSubscribeMessage({
      tmplIds: [WEEKLY_PLAN_TEMPLATE_ID],
      success: (result) => resolve(["accept", "acceptWithAudio"].includes(result[WEEKLY_PLAN_TEMPLATE_ID])),
      fail: () => resolve(false)
    });
  });
}
function replacePlanDay(id, index) {
  return api_request.post(`/weekly-plans/${id}/days/${index}/replace`);
}
function toggleWeeklyPlanFavorite(id) {
  return api_request.post(`/weekly-plans/${id}/favorite`);
}
function generatePlanDishCover(id, dayIndex, dishIndex) {
  return api_request.post(`/weekly-plans/${id}/days/${dayIndex}/cover/${dishIndex}`);
}
function reportPlanDishOutcome(data) {
  return api_request.post("/agent/outcomes", data);
}
function toggleShoppingItem(id, name) {
  return api_request.post(`/weekly-plans/${id}/shopping/${encodeURIComponent(name)}`);
}
exports.generatePlanDishCover = generatePlanDishCover;
exports.generateWeeklyPlan = generateWeeklyPlan;
exports.getCurrentPlan = getCurrentPlan;
exports.getWeeklyPlan = getWeeklyPlan;
exports.getWeeklyPlanHistory = getWeeklyPlanHistory;
exports.replacePlanDay = replacePlanDay;
exports.reportPlanDishOutcome = reportPlanDishOutcome;
exports.requestWeeklyPlanCompletionNotice = requestWeeklyPlanCompletionNotice;
exports.runMealAgentTurn = runMealAgentTurn;
exports.toggleShoppingItem = toggleShoppingItem;
exports.toggleWeeklyPlanFavorite = toggleWeeklyPlanFavorite;
