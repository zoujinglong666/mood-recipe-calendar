"use strict";
const common_vendor = require("../common/vendor.js");
const api_auth = require("../api/auth.js");
const stores_user = require("../stores/user.js");
function normalizeLoginError(e) {
  const msg = e instanceof Error ? e.message : String(e);
  if (/invalid code/i.test(msg)) {
    console.error("[login] invalid code —— 排查：开发者工具需用真实微信账号登录（非游客模式）；manifest 的 mp-weixin.appid 须为真实 appid；后端 code2Session 的 appid/secret 须一致。\n", e);
    return "微信登录连接不上，请稍后重试";
  }
  return msg || "微信登录失败";
}
let loginInFlight = null;
function startWechatLogin() {
  if (loginInFlight)
    return loginInFlight;
  loginInFlight = new Promise((resolve, reject) => {
    common_vendor.index.login({
      provider: "weixin",
      success: async (res) => {
        if (res.code) {
          try {
            const result = await api_auth.login(res.code);
            stores_user.useUserStore().setLogin(result.openid, result.sessionToken, result.user);
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
  }).finally(() => {
    loginInFlight = null;
  });
  return loginInFlight;
}
function ensureLogin() {
  const userStore = stores_user.useUserStore();
  if (userStore.isLoggedIn) {
    return Promise.resolve(userStore.openid);
  }
  if (userStore.userInitiatedLogout) {
    return Promise.reject(new Error("NOT_LOGGED_IN"));
  }
  userStore.restoreFromStorage();
  if (userStore.isLoggedIn) {
    return Promise.resolve(userStore.openid);
  }
  return startWechatLogin();
}
function refreshWechatLoginForPayment() {
  const userStore = stores_user.useUserStore();
  if (userStore.userInitiatedLogout)
    return Promise.reject(new Error("NOT_LOGGED_IN"));
  return startWechatLogin();
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
let loginNavigating = false;
function navigateToLogin() {
  const pages = getCurrentPages();
  const top = pages[pages.length - 1];
  if (top && typeof top.route === "string" && top.route.endsWith("/login"))
    return;
  if (loginNavigating)
    return;
  loginNavigating = true;
  common_vendor.index.navigateTo({ url: "/pages/login/index" }).finally(() => {
    loginNavigating = false;
  });
}
exports.ensureLogin = ensureLogin;
exports.navigateToLogin = navigateToLogin;
exports.refreshUserInfo = refreshUserInfo;
exports.refreshWechatLoginForPayment = refreshWechatLoginForPayment;
