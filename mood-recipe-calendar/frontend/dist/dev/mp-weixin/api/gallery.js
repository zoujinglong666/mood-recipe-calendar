"use strict";
const api_request = require("./request.js");
function doCheckin() {
  return api_request.post("/gallery/checkin");
}
function fetchCheckinStatus() {
  return api_request.get("/gallery/checkin/status");
}
exports.doCheckin = doCheckin;
exports.fetchCheckinStatus = fetchCheckinStatus;
