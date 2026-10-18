"use strict";const t=require("../../../api/request.js");exports.fetchMonthAlbum=function(e){return t.get("/albums/month",{month:e})};
