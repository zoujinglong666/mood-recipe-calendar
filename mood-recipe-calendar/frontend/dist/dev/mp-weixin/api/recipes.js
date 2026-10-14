"use strict";
const api_request = require("./request.js");
function fetchRecipeLibrary(params) {
  return api_request.get("/recipes/library", params).then((res) => ({
    ...res,
    list: (res.list ?? []).map(normalizeRecipe)
  }));
}
function fetchMoodTags() {
  return api_request.get("/recipes/mood-tags").then((tags) => Array.isArray(tags) ? tags : []);
}
function createRecommendationJob(mood, mealType) {
  return api_request.post("/recipes/recommend-jobs", { mood, mealType }).then((job) => ({
    ...job,
    recipe: job.recipe ? normalizeRecipe(job.recipe) : void 0
  }));
}
function fetchRecipeQuota() {
  return api_request.get("/recipes/quota");
}
function fetchRecipePool() {
  return api_request.get("/recipes").then((items) => (Array.isArray(items) ? items : []).map(normalizeRecipe));
}
function fetchRecommendationHistory() {
  return api_request.get("/recipes/history").then((items) => items.map((item) => ({ ...item, image: api_request.resolveAssetUrl(item.image) })));
}
function fetchRecommendationJob(jobId) {
  return api_request.get(`/recipes/recommend-jobs/${jobId}`).then((job) => ({
    ...job,
    recipe: job.recipe ? normalizeRecipe(job.recipe) : void 0
  }));
}
function sendRecipeFeedback(recipe, action) {
  const path = recipe.exposureId ? `/recipes/exposures/${encodeURIComponent(recipe.exposureId)}/feedback` : `/recipes/${recipe.id}/feedback`;
  return api_request.post(path, { action });
}
function fetchRecipeFeedback(recipe) {
  const path = recipe.exposureId ? `/recipes/exposures/${encodeURIComponent(recipe.exposureId)}/feedback` : `/recipes/${recipe.id}/feedback`;
  return api_request.get(path);
}
function requestDeepRecipe(payload) {
  const { openid: _openid, ...request } = payload;
  return api_request.post("/recipes/deep-recommend", request).then(normalizeRecipe);
}
function fetchRecipeDetail(id) {
  return api_request.get(`/recipes/${id}`).then(normalizeRecipe);
}
function normalizeRecipe(recipe) {
  return { ...recipe, source: recipe.source === "AI" ? "AI" : "LOCAL", image: api_request.resolveAssetUrl(recipe.image) };
}
exports.createRecommendationJob = createRecommendationJob;
exports.fetchMoodTags = fetchMoodTags;
exports.fetchRecipeDetail = fetchRecipeDetail;
exports.fetchRecipeFeedback = fetchRecipeFeedback;
exports.fetchRecipeLibrary = fetchRecipeLibrary;
exports.fetchRecipePool = fetchRecipePool;
exports.fetchRecipeQuota = fetchRecipeQuota;
exports.fetchRecommendationHistory = fetchRecommendationHistory;
exports.fetchRecommendationJob = fetchRecommendationJob;
exports.requestDeepRecipe = requestDeepRecipe;
exports.sendRecipeFeedback = sendRecipeFeedback;
