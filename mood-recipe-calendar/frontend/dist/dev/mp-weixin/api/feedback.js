"use strict";
const api_request = require("./request.js");
function submitFeedback(data) {
  return api_request.post("/feedback", data);
}
function fetchMyFeedback() {
  return api_request.get("/feedback/mine");
}
exports.fetchMyFeedback = fetchMyFeedback;
exports.submitFeedback = submitFeedback;
