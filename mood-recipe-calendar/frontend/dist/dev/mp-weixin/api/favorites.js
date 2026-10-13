"use strict";
const api_request = require("./request.js");
function fetchFavorites() {
  return api_request.get("/recipes/favorites").then(
    (items) => (Array.isArray(items) ? items : []).map((item) => ({ ...item, image: item.image ? api_request.resolveAssetUrl(item.image) : null }))
  );
}
function addFavorite(recipeId) {
  return api_request.post(`/recipes/favorites/${encodeURIComponent(recipeId)}`);
}
function removeFavorite(recipeId) {
  return api_request.del(`/recipes/favorites/${encodeURIComponent(recipeId)}`);
}
exports.addFavorite = addFavorite;
exports.fetchFavorites = fetchFavorites;
exports.removeFavorite = removeFavorite;
