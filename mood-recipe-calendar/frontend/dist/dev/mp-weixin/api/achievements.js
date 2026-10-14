"use strict";
const api_request = require("./request.js");
function fetchAchievements() {
  return api_request.get("/achievements");
}
function checkAchievements() {
  return api_request.post("/achievements/check", {});
}
exports.checkAchievements = checkAchievements;
exports.fetchAchievements = fetchAchievements;
