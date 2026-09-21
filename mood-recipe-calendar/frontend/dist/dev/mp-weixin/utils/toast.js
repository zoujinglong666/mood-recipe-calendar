"use strict";
const common_vendor = require("../common/vendor.js");
function toast(msg, duration = 2e3) {
  common_vendor.index.showToast({ title: msg, icon: "none", duration });
}
function toastSuccess(msg, duration = 1500) {
  common_vendor.index.showToast({ title: msg, icon: "success", duration });
}
function toastError(err, fallback = "操作失败，请稍后重试") {
  let msg = err instanceof Error ? err.message || fallback : typeof err === "string" && err ? err : fallback;
  if (!msg || /request:fail|network|timeout|socket|ERR_CONNECTION/i.test(msg))
    msg = fallback;
  common_vendor.index.showToast({ title: msg, icon: "none", duration: 2500 });
}
exports.toast = toast;
exports.toastError = toastError;
exports.toastSuccess = toastSuccess;
