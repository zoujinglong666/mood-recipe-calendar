"use strict";
const api_request = require("./request.js");
function cookingAgentTurn(payload) {
  return api_request.post("/cooking-agent/turn", payload);
}
function sendCookingFeedback(payload) {
  return api_request.post("/cooking-agent/feedback", payload);
}
function clearCookingLearning() {
  return api_request.del("/cooking-agent/learning");
}
exports.clearCookingLearning = clearCookingLearning;
exports.cookingAgentTurn = cookingAgentTurn;
exports.sendCookingFeedback = sendCookingFeedback;
