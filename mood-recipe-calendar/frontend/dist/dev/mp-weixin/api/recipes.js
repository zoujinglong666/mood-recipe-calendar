"use strict";
const api_request = require("./request.js");
function createRecommendationJob(mood) {
  return api_request.post("/recipes/recommend-jobs", { mood }).then((job) => ({
    ...job,
    recipe: job.recipe ? normalizeRecipe(job.recipe) : void 0
  }));
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
exports.fetchRecipeDetail = fetchRecipeDetail;
exports.fetchRecipeFeedback = fetchRecipeFeedback;
exports.fetchRecommendationJob = fetchRecommendationJob;
exports.requestDeepRecipe = requestDeepRecipe;
exports.sendRecipeFeedback = sendRecipeFeedback;
