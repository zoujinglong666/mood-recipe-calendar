"use strict";const e=require("./request.js");exports.doCheckin=function(){return e.post("/gallery/checkin")},exports.fetchCheckinStatus=function(){return e.get("/gallery/checkin/status")};
