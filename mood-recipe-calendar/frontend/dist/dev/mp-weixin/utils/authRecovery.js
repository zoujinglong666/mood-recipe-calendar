"use strict";
const common_vendor = require("../common/vendor.js");
const RETURN_KEY = "mrc_auth_return";
const LOGIN_ROUTE = "pages/login/index";
let recovering = false;
function currentInternalPath() {
  const pages = typeof getCurrentPages === "function" ? getCurrentPages() : [];
  const page = pages[pages.length - 1];
  const route = String((page == null ? void 0 : page.route) || "");
  if (!/^(?:pages|subPages)\//.test(route) || route === LOGIN_ROUTE)
    return "";
  const query = Object.entries((page == null ? void 0 : page.options) || {}).map(([key, value]) => `${encodeURIComponent(key)}=${encodeURIComponent(String(value))}`).join("&");
  return `/${route}${query ? `?${query}` : ""}`;
}
function beginAuthRecovery() {
  if (recovering)
    return false;
  recovering = true;
  const target = currentInternalPath();
  if (target)
    common_vendor.index.setStorageSync(RETURN_KEY, target);
  common_vendor.index.$emit("auth:expired");
  return true;
}
function consumeAuthReturn() {
  const target = String(common_vendor.index.getStorageSync(RETURN_KEY) || "");
  common_vendor.index.removeStorageSync(RETURN_KEY);
  recovering = false;
  return /^(?:\/pages|\/subPages)\//.test(target) ? target : "";
}
exports.beginAuthRecovery = beginAuthRecovery;
exports.consumeAuthReturn = consumeAuthReturn;
