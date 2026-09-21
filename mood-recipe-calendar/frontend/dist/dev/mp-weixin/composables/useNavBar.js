"use strict";
const common_vendor = require("../common/vendor.js");
const FALLBACK = {
  statusBarHeight: 20,
  navBarHeight: 64,
  capsuleWidth: 87,
  capsuleHeight: 32,
  capsuleLeft: 269,
  capsuleRightGap: 106
};
const navMetrics = common_vendor.reactive({ ...FALLBACK });
function refreshNavMetrics() {
  try {
    const info = common_vendor.index.getSystemInfoSync();
    const statusBarHeight = info.statusBarHeight || FALLBACK.statusBarHeight;
    const windowWidth = info.windowWidth || 375;
    const menu = common_vendor.index.getMenuButtonBoundingClientRect();
    const capsuleHeight = menu.height || FALLBACK.capsuleHeight;
    const capsuleLeft = menu.left || FALLBACK.capsuleLeft;
    const navBarHeight = (menu.top - statusBarHeight) * 2 + capsuleHeight;
    navMetrics.statusBarHeight = statusBarHeight;
    navMetrics.navBarHeight = navBarHeight > 0 ? navBarHeight : FALLBACK.navBarHeight;
    navMetrics.capsuleWidth = menu.width || FALLBACK.capsuleWidth;
    navMetrics.capsuleHeight = capsuleHeight;
    navMetrics.capsuleLeft = capsuleLeft;
    navMetrics.capsuleRightGap = Math.max(0, windowWidth - capsuleLeft);
  } catch {
  }
}
function useNavBar() {
  return navMetrics;
}
function navBack() {
  try {
    const pages = getCurrentPages();
    if (pages.length > 1) {
      common_vendor.index.navigateBack();
      return;
    }
  } catch {
  }
  common_vendor.index.switchTab({ url: "/pages/index/index" });
}
exports.navBack = navBack;
exports.refreshNavMetrics = refreshNavMetrics;
exports.useNavBar = useNavBar;
