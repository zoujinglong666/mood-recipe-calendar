"use strict";
const api_request = require("./request.js");
function fetchMonthAlbum(openid, month) {
  return api_request.get("/albums/month", { month });
}
exports.fetchMonthAlbum = fetchMonthAlbum;
