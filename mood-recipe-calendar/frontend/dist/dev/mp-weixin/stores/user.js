"use strict";
const common_vendor = require("../common/vendor.js");
const useUserStore = common_vendor.defineStore("user", () => {
  const openid = common_vendor.ref("");
  const sessionToken = common_vendor.ref("");
  const userInfo = common_vendor.ref(null);
  const userInitiatedLogout = common_vendor.ref(false);
  const isLoggedIn = common_vendor.computed(() => !!openid.value && !!sessionToken.value);
  function setLogin(oid, token, info) {
    openid.value = oid;
    sessionToken.value = token;
    userInitiatedLogout.value = false;
    if (info)
      userInfo.value = info;
    common_vendor.index.setStorageSync("openid", oid);
    common_vendor.index.setStorageSync("sessionToken", token);
    if (info)
      common_vendor.index.setStorageSync("userInfo", JSON.stringify(info));
  }
  function logout() {
    openid.value = "";
    sessionToken.value = "";
    userInfo.value = null;
    userInitiatedLogout.value = true;
    common_vendor.index.removeStorageSync("openid");
    common_vendor.index.removeStorageSync("sessionToken");
    common_vendor.index.removeStorageSync("userInfo");
  }
  function clearLogoutFlag() {
    userInitiatedLogout.value = false;
  }
  function restoreFromStorage() {
    const oid = common_vendor.index.getStorageSync("openid");
    const token = common_vendor.index.getStorageSync("sessionToken");
    if (oid && token) {
      openid.value = oid;
      sessionToken.value = token;
      userInitiatedLogout.value = false;
      const infoStr = common_vendor.index.getStorageSync("userInfo");
      if (infoStr) {
        try {
          userInfo.value = JSON.parse(infoStr);
        } catch {
        }
      }
    }
  }
  return {
    openid,
    sessionToken,
    userInfo,
    isLoggedIn,
    userInitiatedLogout,
    setLogin,
    logout,
    clearLogoutFlag,
    restoreFromStorage
  };
});
exports.useUserStore = useUserStore;
