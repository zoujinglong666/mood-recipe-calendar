"use strict";
const api_request = require("./request.js");
function adminRecipeStats() {
  return api_request.get("/admin/recipes/stats");
}
function adminCheck() {
  return api_request.get("/admin/recipes/check");
}
function adminRecipeList(params) {
  return api_request.get("/admin/recipes", params);
}
function adminRecipeDetail(id) {
  return api_request.get(`/admin/recipes/${id}`);
}
function adminRecipeUpdate(id, data) {
  return api_request.put(`/admin/recipes/${id}`, data);
}
function adminRecipeDelete(id) {
  return api_request.del(`/admin/recipes/${id}`);
}
function adminRecipeGenImage(id) {
  return api_request.post(`/admin/recipes/${id}/image`, {});
}
function adminRecipeBatchImage(count) {
  return api_request.post("/admin/recipes/batch-image", { count });
}
function adminGetGenerationConfig() {
  return api_request.get("/admin/recipes/generation-config");
}
function adminUpdateGenerationConfig(data) {
  return api_request.put("/admin/recipes/generation-config", data);
}
function adminRunGenerationNow(count) {
  return api_request.post("/admin/recipes/generation-run", { count });
}
exports.adminCheck = adminCheck;
exports.adminGetGenerationConfig = adminGetGenerationConfig;
exports.adminRecipeBatchImage = adminRecipeBatchImage;
exports.adminRecipeDelete = adminRecipeDelete;
exports.adminRecipeDetail = adminRecipeDetail;
exports.adminRecipeGenImage = adminRecipeGenImage;
exports.adminRecipeList = adminRecipeList;
exports.adminRecipeStats = adminRecipeStats;
exports.adminRecipeUpdate = adminRecipeUpdate;
exports.adminRunGenerationNow = adminRunGenerationNow;
exports.adminUpdateGenerationConfig = adminUpdateGenerationConfig;
