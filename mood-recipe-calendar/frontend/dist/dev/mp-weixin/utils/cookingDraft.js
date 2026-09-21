"use strict";
const common_vendor = require("../common/vendor.js");
const COOKING_DRAFT_KEY = "mrc_cooking_draft";
const COOKING_PROGRESS_KEY = "mrc_cooking_progress";
const RECORD_DRAFT_KEY = "mrc_record_draft";
function saveCookingDraft(recipe, mood) {
  common_vendor.index.setStorageSync(COOKING_DRAFT_KEY, { recipe, mood, createdAt: Date.now() });
}
function loadCookingDraft() {
  const value = common_vendor.index.getStorageSync(COOKING_DRAFT_KEY);
  return (value == null ? void 0 : value.recipe) ? value : null;
}
function createRequestId() {
  return `${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 12)}`;
}
function saveRecordDraft(recipe, mood) {
  common_vendor.index.setStorageSync(RECORD_DRAFT_KEY, {
    dish: recipe.name,
    mood,
    recipeId: recipe.id,
    exposureId: recipe.exposureId,
    image: recipe.image,
    cookingTime: recipe.cookingTime,
    clientRequestId: createRequestId(),
    source: "recipe"
  });
}
exports.COOKING_PROGRESS_KEY = COOKING_PROGRESS_KEY;
exports.RECORD_DRAFT_KEY = RECORD_DRAFT_KEY;
exports.createRequestId = createRequestId;
exports.loadCookingDraft = loadCookingDraft;
exports.saveCookingDraft = saveCookingDraft;
exports.saveRecordDraft = saveRecordDraft;
