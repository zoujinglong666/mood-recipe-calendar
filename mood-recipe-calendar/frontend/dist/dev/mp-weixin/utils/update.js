"use strict";
const common_vendor = require("../common/vendor.js");
let manager = null;
let updateReady = false;
function ensureManager() {
  if (!manager) {
    manager = common_vendor.index.getUpdateManager();
    manager.onUpdateReady(() => {
      updateReady = true;
      promptApply();
    });
    manager.onUpdateFailed(() => {
      console.warn("[update] 新版本下载失败");
    });
  }
  return manager;
}
function promptApply() {
  common_vendor.index.showModal({
    title: "发现新版本",
    content: "锅仔已准备好新版本，重启后即可体验最新功能与优化。",
    confirmText: "立即更新",
    confirmColor: "#EF5A3C",
    success: (res) => {
      if (res.confirm)
        manager == null ? void 0 : manager.applyUpdate();
    }
  });
}
function autoCheckUpdate() {
  const m = ensureManager();
  if (!m)
    return;
  m.onCheckForUpdate(() => {
  });
}
function manualCheckUpdate() {
  const m = ensureManager();
  if (!m) {
    common_vendor.index.showToast({ title: "已是最新版本", icon: "success" });
    return;
  }
  if (updateReady) {
    promptApply();
    return;
  }
  m.onCheckForUpdate((res) => {
    if (res.hasUpdate)
      common_vendor.index.showToast({ title: "发现新版本，正在准备…", icon: "none" });
    else
      common_vendor.index.showToast({ title: "已是最新版本", icon: "success" });
  });
}
exports.autoCheckUpdate = autoCheckUpdate;
exports.manualCheckUpdate = manualCheckUpdate;
