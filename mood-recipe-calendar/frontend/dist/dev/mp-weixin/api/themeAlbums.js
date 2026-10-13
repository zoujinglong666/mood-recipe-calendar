"use strict";
const api_request = require("./request.js");
function fetchThemeAlbums() {
  return api_request.get("/theme-albums");
}
function fetchThemeAlbumDetail(sku) {
  return api_request.get(`/theme-albums/${encodeURIComponent(sku)}`);
}
exports.fetchThemeAlbumDetail = fetchThemeAlbumDetail;
exports.fetchThemeAlbums = fetchThemeAlbums;
