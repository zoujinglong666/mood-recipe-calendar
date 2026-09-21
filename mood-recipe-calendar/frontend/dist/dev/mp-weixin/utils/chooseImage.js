"use strict";
const common_vendor = require("../common/vendor.js");
const utils_toast = require("./toast.js");
function guideToSettings() {
  common_vendor.index.showModal({
    title: "需要相册权限",
    content: "开启相册/相机权限后，才能拍照或从相册选择照片记录美食。",
    confirmText: "去设置",
    cancelText: "取消",
    success: (res) => {
      if (res.confirm)
        common_vendor.index.openSetting();
    }
  });
}
function guideToPrivacy() {
  common_vendor.index.showModal({
    title: "需要同意隐私保护指引",
    content: "同意《用户隐私保护指引》后，才能使用相册功能。",
    confirmText: "查看指引",
    cancelText: "取消",
    success: (res) => {
      if (res.confirm && common_vendor.wx$1.openPrivacyContract)
        common_vendor.wx$1.openPrivacyContract({});
    }
  });
}
function chooseImageFile(options) {
  const msg = (err) => String((err == null ? void 0 : err.errMsg) || "");
  common_vendor.index.chooseMedia({
    count: 1,
    mediaType: ["image"],
    sourceType: ["album", "camera"],
    sizeType: ["compressed"],
    success: (res) => {
      var _a, _b;
      const filePath = (_b = (_a = res == null ? void 0 : res.tempFiles) == null ? void 0 : _a[0]) == null ? void 0 : _b.tempFilePath;
      if (filePath)
        options.onSelected(filePath);
    },
    fail: (err) => {
      const errMsg = msg(err);
      if (errMsg.includes("cancel"))
        return;
      if (errMsg.includes("auth deny") || errMsg.includes("authorize") || errMsg.includes("permission"))
        return guideToSettings();
      if (errMsg.includes("privacy"))
        return guideToPrivacy();
      if (options.onFail)
        options.onFail();
      else
        utils_toast.toast("选择图片失败，请重试");
    }
  });
}
exports.chooseImageFile = chooseImageFile;
