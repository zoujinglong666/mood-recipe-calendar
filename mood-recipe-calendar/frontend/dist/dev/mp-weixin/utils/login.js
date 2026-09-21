"use strict";
const common_vendor = require("../common/vendor.js");
const api_auth = require("../api/auth.js");
const stores_user = require("../stores/user.js");
function normalizeLoginError(e) {
  const msg = e instanceof Error ? e.message : String(e);
  if (/invalid code/i.test(msg)) {
    return "微信登录失败：code 无效（invalid code）。请确认：\n1) 微信开发者工具已用真实微信账号登录（非游客模式）；\n2) manifest.json 的 mp-weixin.appid 已填写为真实小程序 appid；\n3) 后端 code2Session 使用的 appid / secret 与该 appid 一致。";
  }
  return msg || "微信登录失败";
}
async function ensureLogin() {
  const userStore = stores_user.useUserStore();
  if (userStore.isLoggedIn) {
    return userStore.openid;
  }
  if (userStore.userInitiatedLogout) {
    throw new Error("NOT_LOGGED_IN");
  }
  userStore.restoreFromStorage();
  if (userStore.isLoggedIn) {
    return userStore.openid;
  }
  return new Promise((resolve, reject) => {
    common_vendor.index.login({
      provider: "weixin",
      success: async (res) => {
        if (res.code) {
          try {
            const result = await api_auth.login(res.code);
            userStore.setLogin(result.openid, result.sessionToken, result.user);
            resolve(result.openid);
          } catch (e) {
            reject(new Error(normalizeLoginError(e)));
          }
        } else {
          reject(new Error("微信登录失败：未获取到 code"));
        }
      },
      fail: () => reject(new Error("微信登录失败"))
    });
  });
}
let lastUserInfoRefresh = 0;
const USER_INFO_CACHE_MS = 5 * 60 * 1e3;
async function refreshUserInfo(force = false) {
  const userStore = stores_user.useUserStore();
  if (!userStore.openid)
    return;
  if (!force && Date.now() - lastUserInfoRefresh < USER_INFO_CACHE_MS)
    return;
  const { getUserInfo } = await require.async("../api/auth.js");
  try {
    const info = await getUserInfo(userStore.openid);
    userStore.setLogin(userStore.openid, userStore.sessionToken, info);
    lastUserInfoRefresh = Date.now();
  } catch {
  }
}
exports.ensureLogin = ensureLogin;
exports.refreshUserInfo = refreshUserInfo;
