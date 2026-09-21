"use strict";
const common_vendor = require("../common/vendor.js");
const utils_authRecovery = require("../utils/authRecovery.js");
const API_BACKENDS = [
  { name: "本地开发", url: "http://10.18.0.19:8080/api", env: "development", desc: "10.18.0.19:8080" },
  { name: "生产环境", url: "https://moodrecipe.icu/api", env: "production", desc: "moodrecipe.icu" }
];
const STORAGE_KEY = "apiBaseUrl";
function getApiBaseUrl() {
  try {
    const saved = common_vendor.index.getStorageSync(STORAGE_KEY);
    if (saved)
      return String(saved);
  } catch {
  }
  return "http://10.18.0.19:8080/api";
}
function setApiBaseUrl(url) {
  try {
    common_vendor.index.setStorageSync(STORAGE_KEY, url);
  } catch {
  }
}
function resolveAssetUrl(url) {
  if (!url || /^(?:https?:)?\/\//.test(url) || url.startsWith("data:"))
    return url || "";
  const origin = getApiBaseUrl().replace(/\/api\/?$/, "");
  return `${origin}${url.startsWith("/") ? "" : "/"}${url}`;
}
function clearLocalAuth() {
  try {
    common_vendor.index.removeStorageSync("openid");
    common_vendor.index.removeStorageSync("sessionToken");
    common_vendor.index.removeStorageSync("userInfo");
    utils_authRecovery.beginAuthRecovery();
  } catch {
  }
}
function isAuthExpired(res) {
  if ((res == null ? void 0 : res.statusCode) === 401)
    return true;
  const data = res == null ? void 0 : res.data;
  return (data == null ? void 0 : data.code) === 401 || /登录.*过期|未登录|请重新登录/.test((data == null ? void 0 : data.message) || "");
}
function authHeader() {
  const token = common_vendor.index.getStorageSync("sessionToken");
  return token ? { "X-Session-Token": String(token) } : {};
}
function handleResponse(res, resolve, reject) {
  if (isAuthExpired(res)) {
    clearLocalAuth();
    reject(new Error("登录已过期，请重新登录"));
    return;
  }
  const data = res.data;
  if (data && data.code === 0) {
    resolve(data.data);
  } else {
    reject(new Error((data == null ? void 0 : data.message) || "请求失败"));
  }
}
function get(url, params) {
  return new Promise((resolve, reject) => {
    const query = params ? `?${Object.entries(params).filter(([, v]) => v !== void 0 && v !== null).map(([k, v]) => `${k}=${encodeURIComponent(String(v))}`).join("&")}` : "";
    common_vendor.index.request({
      url: getApiBaseUrl() + url + query,
      method: "GET",
      header: authHeader(),
      success: (res) => handleResponse(res, resolve, reject),
      fail: (err) => reject(new Error(err.errMsg || "网络错误"))
    });
  });
}
function post(url, data) {
  return new Promise((resolve, reject) => {
    common_vendor.index.request({
      url: getApiBaseUrl() + url,
      method: "POST",
      timeout: 6e4,
      data,
      header: { "Content-Type": "application/json", ...authHeader() },
      success: (res) => handleResponse(res, resolve, reject),
      fail: (err) => reject(new Error(err.errMsg || "网络错误"))
    });
  });
}
function put(url, data) {
  return new Promise((resolve, reject) => {
    common_vendor.index.request({
      url: getApiBaseUrl() + url,
      method: "PUT",
      data,
      header: { "Content-Type": "application/json", ...authHeader() },
      success: (res) => handleResponse(res, resolve, reject),
      fail: (err) => reject(new Error(err.errMsg || "网络错误"))
    });
  });
}
function del(url, data) {
  return new Promise((resolve, reject) => {
    common_vendor.index.request({
      url: getApiBaseUrl() + url,
      method: "DELETE",
      data,
      header: { "Content-Type": "application/json", ...authHeader() },
      success: (res) => handleResponse(res, resolve, reject),
      fail: (err) => reject(new Error(err.errMsg || "网络错误"))
    });
  });
}
function uploadFile(filePath, type = "image") {
  return new Promise((resolve, reject) => {
    common_vendor.index.uploadFile({
      url: `${getApiBaseUrl()}/upload/image?type=${type}`,
      filePath,
      name: "file",
      header: authHeader(),
      success: (res) => {
        if (isAuthExpired(res)) {
          clearLocalAuth();
          reject(new Error("登录已过期，请重新登录"));
          return;
        }
        try {
          const result = JSON.parse(res.data);
          if (result.code === 0) {
            resolve({ ...result.data, url: resolveAssetUrl(result.data.url) });
          } else {
            reject(new Error(result.message || "上传失败"));
          }
        } catch {
          reject(new Error("上传响应解析失败"));
        }
      },
      fail: (err) => reject(new Error(err.errMsg || "上传失败"))
    });
  });
}
exports.API_BACKENDS = API_BACKENDS;
exports.del = del;
exports.get = get;
exports.getApiBaseUrl = getApiBaseUrl;
exports.post = post;
exports.put = put;
exports.resolveAssetUrl = resolveAssetUrl;
exports.setApiBaseUrl = setApiBaseUrl;
exports.uploadFile = uploadFile;
