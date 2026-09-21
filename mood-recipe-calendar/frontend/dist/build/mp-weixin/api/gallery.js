"use strict";const e=require("./request.js");exports.doCheckin=function(t){return e.post("/gallery/checkin")},exports.fetchCheckinStatus=function(t){return e.get("/gallery/checkin/status")};
