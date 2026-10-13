"use strict";
const api_request = require("./request.js");
function fetchNutritionReport() {
  return api_request.get("/nutrition-report/current");
}
exports.fetchNutritionReport = fetchNutritionReport;
