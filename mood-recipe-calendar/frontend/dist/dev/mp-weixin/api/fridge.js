"use strict";
const api_request = require("./request.js");
function fetchFridgeItems() {
  return api_request.get("/fridge/items");
}
function fetchFridgeSummary() {
  return api_request.get("/fridge/summary");
}
function createFridgeItem(data) {
  return api_request.post("/fridge/items", data);
}
function updateFridgeItem(id, data) {
  return api_request.put(`/fridge/items/${id}`, data);
}
function consumeFridgeItem(id, amount = 1) {
  return api_request.post(`/fridge/items/${id}/consume`, { amount });
}
function deleteFridgeItem(id) {
  return api_request.del(`/fridge/items/${id}`);
}
function fetchStorageAdvice(name) {
  return api_request.get(`/fridge/storage-advice?name=${encodeURIComponent(name)}`);
}
function recognizeFridgeImage(imageUrl) {
  return api_request.post("/fridge/recognize", { imageUrl });
}
function notifyFridgeExpiring() {
  return api_request.post("/fridge/notify-expiring", {});
}
exports.consumeFridgeItem = consumeFridgeItem;
exports.createFridgeItem = createFridgeItem;
exports.deleteFridgeItem = deleteFridgeItem;
exports.fetchFridgeItems = fetchFridgeItems;
exports.fetchFridgeSummary = fetchFridgeSummary;
exports.fetchStorageAdvice = fetchStorageAdvice;
exports.notifyFridgeExpiring = notifyFridgeExpiring;
exports.recognizeFridgeImage = recognizeFridgeImage;
exports.updateFridgeItem = updateFridgeItem;
