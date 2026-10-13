"use strict";
const common_vendor = require("../common/vendor.js");
function wxReady() {
  return typeof common_vendor.wx$1 !== "undefined" && common_vendor.wx$1 && typeof common_vendor.wx$1.getWindowInfo === "function";
}
function getWindowInfo() {
  if (wxReady())
    return common_vendor.wx$1.getWindowInfo();
  return common_vendor.index.getSystemInfoSync();
}
function getAppBaseInfo() {
  if (typeof common_vendor.wx$1 !== "undefined" && common_vendor.wx$1 && typeof common_vendor.wx$1.getAppBaseInfo === "function")
    return common_vendor.wx$1.getAppBaseInfo();
  return common_vendor.index.getSystemInfoSync();
}
exports.getAppBaseInfo = getAppBaseInfo;
exports.getWindowInfo = getWindowInfo;
