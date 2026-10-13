"use strict";
const api_request = require("./request.js");
function normalizeBoard(board) {
  var _a;
  const visibleText = (value) => typeof value !== "string" || !/[?？�]/.test(value);
  if (!(board == null ? void 0 : board.recipe) || !visibleText(board.recipe.name) || !visibleText(board.recipe.description) || !visibleText(board.recipe.ingredients) || !visibleText(board.recipe.steps) || !visibleText(board.guozaiLine)) {
    throw new Error("今日菜单数据异常，已拦截展示");
  }
  return { ...board, recipe: { ...board.recipe, image: api_request.resolveAssetUrl(((_a = board.recipe) == null ? void 0 : _a.image) || "") } };
}
function fetchDailyBoard() {
  return api_request.get("/daily-menu").then(normalizeBoard);
}
function refreshDailyBoard() {
  return api_request.post("/daily-menu/refresh", {}).then(normalizeBoard);
}
exports.fetchDailyBoard = fetchDailyBoard;
exports.refreshDailyBoard = refreshDailyBoard;
