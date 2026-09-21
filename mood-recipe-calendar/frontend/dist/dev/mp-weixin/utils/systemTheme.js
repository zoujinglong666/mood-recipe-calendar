"use strict";
const common_vendor = require("../common/vendor.js");
const themeOwnerStates = /* @__PURE__ */ new WeakMap();
function getThemeOwnerState(owner) {
  let state = themeOwnerStates.get(owner);
  if (!state) {
    state = {
      initialized: false,
      listenerCount: 0
    };
    themeOwnerStates.set(owner, state);
  }
  return state;
}
function isThemeMode(theme) {
  return theme === "light" || theme === "dark";
}
function getSystemTheme() {
  try {
    const appBaseInfo = common_vendor.index.getAppBaseInfo();
    if (isThemeMode(appBaseInfo == null ? void 0 : appBaseInfo.theme)) {
      return appBaseInfo.theme;
    }
  } catch (error) {
    console.warn("获取系统主题失败:", error);
  }
  return "light";
}
function initializeThemeOnce(owner, initialize) {
  const state = getThemeOwnerState(owner);
  if (state.initialized) {
    return;
  }
  initialize();
  state.initialized = true;
}
function subscribeSystemThemeChange(owner, handler) {
  if (typeof common_vendor.index === "undefined" || typeof common_vendor.index.onThemeChange !== "function") {
    return () => {
    };
  }
  const state = getThemeOwnerState(owner);
  if (!state.themeChangeHandler) {
    state.themeChangeHandler = handler;
    common_vendor.index.onThemeChange(state.themeChangeHandler);
  }
  state.listenerCount += 1;
  let subscribed = true;
  return () => {
    if (!subscribed) {
      return;
    }
    subscribed = false;
    state.listenerCount = Math.max(0, state.listenerCount - 1);
    if (state.listenerCount === 0 && state.themeChangeHandler) {
      if (typeof common_vendor.index.offThemeChange === "function") {
        common_vendor.index.offThemeChange(state.themeChangeHandler);
      }
      state.themeChangeHandler = void 0;
    }
  };
}
exports.getSystemTheme = getSystemTheme;
exports.initializeThemeOnce = initializeThemeOnce;
exports.subscribeSystemThemeChange = subscribeSystemThemeChange;
