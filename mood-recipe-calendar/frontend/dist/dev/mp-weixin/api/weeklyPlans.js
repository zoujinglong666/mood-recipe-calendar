"use strict";
const common_vendor = require("../common/vendor.js");
const api_request = require("./request.js");
function requestId(prefix) {
  return `${prefix}-${Date.now()}-${Math.random().toString(36).slice(2, 10)}`;
}
const SLOW_TIMEOUT = 6e4;
function getCurrentPlan() {
  return api_request.get("/weekly-plans/current");
}
function getWeeklyPlan(id) {
  return api_request.get(`/weekly-plans/${id}`);
}
function getWeeklyPlanHistory() {
  return api_request.get("/weekly-plans/history");
}
function fetchAgentConversationQuota() {
  return api_request.get("/weekly-plans/agent-quota");
}
function fetchCurrentAgentConversation() {
  return api_request.get("/weekly-plans/agent-conversations/current");
}
function resetAgentConversation() {
  return api_request.post("/weekly-plans/agent-conversations/reset");
}
function generateWeeklyPlan(data) {
  return api_request.post("/weekly-plans/generate", { ...data, requestId: data.requestId || requestId("plan") });
}
function getWeeklyPlanJob(jobId) {
  return api_request.get(`/weekly-plans/generate/jobs/${jobId}`);
}
function runMealAgentTurn(message, state, conversationId, history, webSearch, preset) {
  return api_request.post("/weekly-plans/agent-turns", { message, state, conversationId, requestId: requestId("turn"), history, ...webSearch ? { webSearch } : {}, ...preset ? { preset } : {} });
}
function rateAgentConversation(rating, comment) {
  return api_request.post("/weekly-plans/agent-feedback", { rating, ...{} });
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
  return api_request.post(`/weekly-plans/${id}/days/${index}/replace`, void 0, SLOW_TIMEOUT);
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
exports.fetchAgentConversationQuota = fetchAgentConversationQuota;
exports.fetchCurrentAgentConversation = fetchCurrentAgentConversation;
exports.generatePlanDishCover = generatePlanDishCover;
exports.generateWeeklyPlan = generateWeeklyPlan;
exports.getCurrentPlan = getCurrentPlan;
exports.getWeeklyPlan = getWeeklyPlan;
exports.getWeeklyPlanHistory = getWeeklyPlanHistory;
exports.getWeeklyPlanJob = getWeeklyPlanJob;
exports.rateAgentConversation = rateAgentConversation;
exports.replacePlanDay = replacePlanDay;
exports.reportPlanDishOutcome = reportPlanDishOutcome;
exports.requestWeeklyPlanCompletionNotice = requestWeeklyPlanCompletionNotice;
exports.resetAgentConversation = resetAgentConversation;
exports.runMealAgentTurn = runMealAgentTurn;
exports.toggleShoppingItem = toggleShoppingItem;
exports.toggleWeeklyPlanFavorite = toggleWeeklyPlanFavorite;
