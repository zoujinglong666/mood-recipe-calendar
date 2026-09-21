"use strict";
const api_request = require("./request.js");
function doCheckin(openid) {
  return api_request.post("/gallery/checkin");
}
function fetchCheckinStatus(openid) {
  return api_request.get("/gallery/checkin/status");
}
exports.doCheckin = doCheckin;
exports.fetchCheckinStatus = fetchCheckinStatus;
