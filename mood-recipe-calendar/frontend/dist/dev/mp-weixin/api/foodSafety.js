"use strict";
const api_request = require("./request.js");
function fetchFoodTips(names) {
  const q = names.map((n) => n.trim()).filter(Boolean).slice(0, 40).join(",");
  if (!q)
    return Promise.resolve([]);
  return api_request.get("/food-safety/tips", { names: q }).then((res) => Array.isArray(res == null ? void 0 : res.tips) ? res.tips : []);
}
exports.fetchFoodTips = fetchFoodTips;
