"use strict";const x=require("./request.js");exports.REWARDED_AD_UNIT_ID="adunit-xxxxxxxxxxxxxx",exports.watchAdReward=function(t,e){return x.post("/ad/reward",{type:t,adUnitId:e})};
