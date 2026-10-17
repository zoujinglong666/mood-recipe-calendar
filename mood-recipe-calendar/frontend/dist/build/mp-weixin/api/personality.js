"use strict";const e=require("./request.js");exports.analyzePersonality=function(t,s="eat"){return e.post("/personality/analyze",{answers:t,test:s},5e4)};
