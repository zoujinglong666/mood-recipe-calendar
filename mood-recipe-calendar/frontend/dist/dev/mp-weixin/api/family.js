"use strict";
const api_request = require("./request.js");
function fetchFamily() {
  return api_request.get("/family");
}
function createFamily() {
  return api_request.post("/family");
}
function joinFamily(code) {
  return api_request.post("/family/join", { code });
}
function leaveFamily() {
  return api_request.post("/family/leave");
}
exports.createFamily = createFamily;
exports.fetchFamily = fetchFamily;
exports.joinFamily = joinFamily;
exports.leaveFamily = leaveFamily;
