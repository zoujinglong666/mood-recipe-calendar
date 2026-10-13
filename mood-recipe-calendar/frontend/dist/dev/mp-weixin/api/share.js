"use strict";
const api_request = require("./request.js");
function recordShare(scene) {
  return api_request.post("/share/record", { scene });
}
function fetchShareStatus() {
  return api_request.get("/share/status");
}
exports.fetchShareStatus = fetchShareStatus;
exports.recordShare = recordShare;
