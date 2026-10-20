"use strict";const t=require("./request.js");exports.watchAdReward=function(e,r){return t.post("/ad/reward",{type:e,adUnitId:r})};
