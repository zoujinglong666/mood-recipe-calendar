"use strict";
const api_request = require("./request.js");
function fetchFoodPreference() {
  return api_request.get("/preferences");
}
function fetchFoodMemory() {
  return api_request.get("/preferences/summary");
}
function saveFoodPreference(data) {
  return api_request.put("/preferences", data);
}
function clearFoodPreference() {
  return api_request.del("/preferences");
}
exports.clearFoodPreference = clearFoodPreference;
exports.fetchFoodMemory = fetchFoodMemory;
exports.fetchFoodPreference = fetchFoodPreference;
exports.saveFoodPreference = saveFoodPreference;
