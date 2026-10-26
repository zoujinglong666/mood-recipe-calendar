"use strict";const t=require("./request.js");exports.doCheckin=function(){return t.post("/checkin")},exports.fetchCheckinStatus=function(){return t.get("/checkin/status")};
