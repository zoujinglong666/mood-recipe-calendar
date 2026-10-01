"use strict";const t=require("./request.js");exports.fetchMonthAlbum=function(e){return t.get("/albums/month",{month:e})};
