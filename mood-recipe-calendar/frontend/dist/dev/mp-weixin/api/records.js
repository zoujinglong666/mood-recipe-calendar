"use strict";
const api_request = require("./request.js");
function saveRecord(payload) {
  const { openid: _openid, ...request } = payload;
  return api_request.post("/records", request).then(normalizeRecord);
}
function fetchRecords(openid) {
  return api_request.get("/records", { openid }).then((items) => items.map(normalizeRecord));
}
function fetchRecordsByMonth(openid, month) {
  return api_request.get("/records/month", { openid, month }).then((items) => items.map(normalizeRecord));
}
function normalizeRecord(record) {
  return { ...record, imageUrl: api_request.resolveAssetUrl(record.imageUrl) };
}
function deleteRecord(id, openid) {
  return api_request.del(`/records/${id}?openid=${encodeURIComponent(openid)}`);
}
function fetchStats(openid) {
  return api_request.get("/records/stats", { openid });
}
function fetchYearStats(openid, year) {
  return api_request.get("/records/year-stats", { openid, year });
}
function fetchCompanionMessage(hour) {
  return api_request.get("/companion/message", { hour });
}
exports.deleteRecord = deleteRecord;
exports.fetchCompanionMessage = fetchCompanionMessage;
exports.fetchRecords = fetchRecords;
exports.fetchRecordsByMonth = fetchRecordsByMonth;
exports.fetchStats = fetchStats;
exports.fetchYearStats = fetchYearStats;
exports.saveRecord = saveRecord;
