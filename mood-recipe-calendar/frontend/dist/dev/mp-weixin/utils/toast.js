"use strict";
const common_vendor = require("../common/vendor.js");
const utils_authRecovery = require("./authRecovery.js");
function toast(msg, duration = 2e3) {
  common_vendor.index.showToast({ title: msg, icon: "none", duration });
}
function toastSuccess(msg, duration = 1500) {
  common_vendor.index.showToast({ title: msg, icon: "success", duration });
}
function toastError(err, fallback = "操作失败，请稍后重试") {
  if (err !== void 0 && err !== null)
    console.warn("[toastError]", fallback, "->", err);
  else
    console.warn("[toastError]", fallback);
  let msg = fallback;
  let isCancel = false;
  if (err instanceof Error) {
    msg = err.message || fallback;
  } else if (err && typeof err === "object") {
    const obj = err;
    const raw = obj.errMsg ?? obj.err_msg ?? obj.message;
    if (typeof raw === "string" && raw) {
      msg = raw;
      isCancel = /cancel/i.test(raw);
    }
  } else if (typeof err === "string" && err) {
    msg = err;
    isCancel = /cancel/i.test(err);
  }
  if (!msg || /request:fail|network|socket|ERR_CONNECTION/i.test(msg))
    msg = fallback;
  if (isCancel)
    msg = "已取消支付";
  if (/^NOT_LOGGED_IN$/.test(msg)) {
    common_vendor.index.showToast({ title: "请先登录", icon: "none", duration: 2e3 });
    utils_authRecovery.beginAuthRecovery();
    return;
  }
  common_vendor.index.showToast({ title: msg, icon: "none", duration: 2500 });
}
function showLoading(msg = "加载中...") {
  common_vendor.index.showLoading({ title: msg, mask: true });
}
function hideLoading() {
  common_vendor.index.hideLoading();
}
exports.hideLoading = hideLoading;
exports.showLoading = showLoading;
exports.toast = toast;
exports.toastError = toastError;
exports.toastSuccess = toastSuccess;
