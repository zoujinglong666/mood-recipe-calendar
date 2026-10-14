"use strict";
const api_request = require("./request.js");
function recordShare(scene) {
  return api_request.post("/share/record", { scene });
}
function acceptShareInvite(sharer) {
  return api_request.post("/share/accept", { scene: sharer });
}
function fetchShareStatus() {
  return api_request.get("/share/status");
}
exports.acceptShareInvite = acceptShareInvite;
exports.fetchShareStatus = fetchShareStatus;
exports.recordShare = recordShare;
