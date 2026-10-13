"use strict";
const api_request = require("./request.js");
function saveRecord(payload) {
  const { openid: _openid, ...request } = payload;
  return api_request.post("/records", request).then((result) => ({
    ...result,
    record: normalizeRecord(result.record)
  }));
}
function fetchRecord(id) {
  return api_request.get(`/records/${id}`).then(normalizeRecord);
}
function updateRecord(id, payload) {
  const { openid: _openid, ...request } = payload;
  return api_request.put(`/records/${id}`, request).then(normalizeRecord);
}
function fetchRecords() {
  return api_request.get("/records").then((items) => mapRecords(items, "GET /records"));
}
function fetchRecordsByMonth(month) {
  return api_request.get("/records/month", { month }).then((items) => mapRecords(items, "GET /records/month"));
}
function mapRecords(items, source) {
  if (!Array.isArray(items)) {
    console.warn(`[records] ${source} 返回非数组:`, typeof items, items);
    return [];
  }
  return items.filter((item, index) => {
    if (item && typeof item === "object")
      return true;
    console.warn(`[records] ${source} 第 ${index} 条记录异常:`, JSON.stringify(item));
    return false;
  }).map(normalizeRecord);
}
function normalizeRecord(record) {
  var _a;
  if (!record || typeof record !== "object") {
    console.warn("[records] normalizeRecord 收到非法记录:", record);
    record = { id: 0, openid: "", imageUrl: "", imageUrls: [], dishName: "", moodTag: "", recordDate: "" };
  }
  const imageUrls = (((_a = record.imageUrls) == null ? void 0 : _a.length) ? record.imageUrls : [record.imageUrl]).filter(Boolean).map(api_request.resolveAssetUrl);
  return { ...record, imageUrl: imageUrls[0] || api_request.resolveAssetUrl(record.imageUrl), imageUrls };
}
function deleteRecord(id) {
  return api_request.del(`/records/${id}`);
}
function fetchStats() {
  return api_request.get("/records/stats");
}
function fetchYearStats(year) {
  return api_request.get("/records/year-stats", { year });
}
function fetchCompanionMessage(hour, date) {
  return api_request.get("/companion/message", { hour, date });
}
exports.deleteRecord = deleteRecord;
exports.fetchCompanionMessage = fetchCompanionMessage;
exports.fetchRecord = fetchRecord;
exports.fetchRecords = fetchRecords;
exports.fetchRecordsByMonth = fetchRecordsByMonth;
exports.fetchStats = fetchStats;
exports.fetchYearStats = fetchYearStats;
exports.saveRecord = saveRecord;
exports.updateRecord = updateRecord;
