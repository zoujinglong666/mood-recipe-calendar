"use strict";
const common_vendor = require("../common/vendor.js");
const utils_toast = require("./toast.js");
function copyText(text) {
  return new Promise((resolve, reject) => {
    common_vendor.index.setClipboardData({
      data: text,
      showToast: false,
      success: () => resolve(),
      fail: () => reject(new Error("复制失败"))
    });
  });
}
async function goToMeituanBuy(keyword, fallbackCopy) {
  try {
    await copyText(fallbackCopy);
    utils_toast.toastSuccess("清单已复制，打开拼多多/超市搜索即可");
  } catch {
    utils_toast.toast("复制失败，请手动记录");
  }
}
exports.goToMeituanBuy = goToMeituanBuy;
