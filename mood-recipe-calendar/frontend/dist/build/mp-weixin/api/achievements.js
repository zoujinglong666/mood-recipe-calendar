"use strict";const e=require("./request.js");exports.checkAchievements=function(){return e.post("/achievements/check",{})},exports.fetchAchievements=function(){return e.get("/achievements")};
