"use strict";
const api_request = require("./request.js");
function generateRecordPoster(id) {
  return api_request.post(`/records/${id}/poster`, void 0, 6e4);
}
exports.generateRecordPoster = generateRecordPoster;
