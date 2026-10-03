"use strict";const e=require("./request.js");exports.fetchShareStatus=function(){return e.get("/share/status")},exports.recordShare=function(r){return e.post("/share/record",{scene:r})};
