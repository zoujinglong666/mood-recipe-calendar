"use strict";const e=require("../../../api/request.js");exports.fetchMyFeedback=function(){return e.get("/feedback/mine")},exports.submitFeedback=function(t){return e.post("/feedback",t)};
