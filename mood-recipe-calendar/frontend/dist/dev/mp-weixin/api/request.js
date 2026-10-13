"use strict";
const common_vendor = require("../common/vendor.js");
const utils_authRecovery = require("../utils/authRecovery.js");
const DEV_ONLY_BACKENDS = [
  { name: "本地开发", url: "http://localhost:8080/api", env: "development", desc: "localhost:8080" },
  { name: "局域网开发", url: "http://10.40.173.23:8080/api", env: "development", desc: "10.40.173.23:8080（本机局域网）" }
];
const PROD_BACKENDS = [
  { name: "生产环境", url: "https://moodrecipe.icu/api", env: "production", desc: "moodrecipe.icu" }
];
const API_BACKENDS = [...DEV_ONLY_BACKENDS, ...PROD_BACKENDS];
const STORAGE_KEY = "apiBaseUrl";
function isTrustedApiUrl(url) {
  if (!url || !/^https?:\/\//.test(url))
    return false;
  try {
    const host = new URL(url).hostname.toLowerCase();
    if (true)
      return true;
    return !/^(10\.|192\.168\.|172\.(1[6-9]|2\d|3[01])\.|127\.|localhost|0\.0\.0\.0)$/.test(host) && !/^\d+\.\d+\.\d+\.\d+$/.test(host);
  } catch {
    return false;
  }
}
function isKnownBackend(url) {
  return API_BACKENDS.some((b) => b.url === url);
}
function getApiBaseUrl() {
  try {
    const saved = common_vendor.index.getStorageSync(STORAGE_KEY);
    if (saved && (isKnownBackend(String(saved)) || isTrustedApiUrl(String(saved))))
      return String(saved);
  } catch {
  }
  return "http://10.18.0.19:8080/api";
}
function setApiBaseUrl(url) {
  if (!url || !/^https?:\/\//.test(url)) {
    common_vendor.index.showToast({ title: "地址格式不正确", icon: "none" });
    return false;
  }
  try {
    if (!new URL(url).hostname) {
      common_vendor.index.showToast({ title: "地址格式不正确", icon: "none" });
      return false;
    }
    common_vendor.index.setStorageSync(STORAGE_KEY, url);
    return true;
  } catch {
    common_vendor.index.showToast({ title: "地址格式不正确", icon: "none" });
    return false;
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
const DEFAULT_TIMEOUT = 15e3;
function uniRequest(opts) {
  const query = opts.params ? `?${Object.entries(opts.params).filter(([, v]) => v !== void 0 && v !== null).map(([k, v]) => `${k}=${encodeURIComponent(String(v))}`).join("&")}` : "";
  return new Promise((resolve, reject) => {
    common_vendor.index.request({
      url: getApiBaseUrl() + opts.url + query,
      method: opts.method,
      timeout: opts.timeout ?? DEFAULT_TIMEOUT,
      data: opts.data,
      header: { "Content-Type": "application/json", ...authHeader() },
      success: (res) => handleResponse(res, resolve, reject),
      fail: (err) => reject(new Error(err.errMsg || "网络错误"))
    });
  });
}
function get(url, params, timeout) {
  return uniRequest({ url, method: "GET", params, timeout });
}
function post(url, data, timeout = 6e4, params) {
  return uniRequest({ url, method: "POST", data, timeout, params });
}
function put(url, data, timeout, params) {
  return uniRequest({ url, method: "PUT", data, timeout, params });
}
function del(url, data, timeout, params) {
  return uniRequest({ url, method: "DELETE", data, timeout, params });
}
function ensureUploadablePath(filePath) {
  if (!/^https?:\/\//i.test(filePath) || /^https?:\/\/tmp\//i.test(filePath))
    return Promise.resolve(filePath);
  console.log("[upload] 远程图片路径，先下载为临时文件:", filePath);
  return new Promise((resolve, reject) => {
    common_vendor.index.downloadFile({
      url: filePath,
      success: (res) => {
        if (res.statusCode === 200 && res.tempFilePath) {
          console.log("[upload] 远程图片下载完成:", res.tempFilePath);
          resolve(res.tempFilePath);
        } else {
          console.warn("[upload] 远程图片下载失败: HTTP", res.statusCode);
          reject(new Error(`素材下载失败(HTTP ${res.statusCode})`));
        }
      },
      fail: (err) => reject(new Error(err.errMsg || "素材下载失败"))
    });
  });
}
function uploadFile(filePath, type = "image") {
  const startedAt = Date.now();
  return ensureUploadablePath(filePath).then((localPath) => new Promise((resolve, reject) => {
    console.log(`[upload] 开始上传 type=${type} filePath=${localPath}`);
    common_vendor.index.uploadFile({
      url: `${getApiBaseUrl()}/upload/image?type=${type}`,
      filePath: localPath,
      name: "file",
      // 真机弱网下默认无超时会一直 pending，界面卡「上传中」；60s 后明确失败
      timeout: 6e4,
      header: authHeader(),
      success: (res) => {
        var _a;
        console.log(`[upload] 收到响应 HTTP ${res.statusCode} 耗时 ${((Date.now() - startedAt) / 1e3).toFixed(1)}s`);
        if (isAuthExpired(res)) {
          reject(new Error("登录已过期，请重新登录"));
          return;
        }
        try {
          const result = JSON.parse(res.data);
          if (result.code === 0) {
            console.log("[upload] 上传成功 url=", (_a = result.data) == null ? void 0 : _a.url);
            resolve({ ...result.data, url: resolveAssetUrl(result.data.url) });
          } else {
            console.warn("[upload] 业务失败:", result.code, result.message);
            reject(new Error(result.message || `上传失败(HTTP ${res.statusCode})`));
          }
        } catch {
          const snippet = typeof res.data === "string" ? res.data.slice(0, 80) : "";
          console.warn("[upload] 响应非 JSON:", res.statusCode, snippet);
          reject(new Error(`上传响应解析失败(HTTP ${res.statusCode})${snippet ? `: ${snippet}` : ""}`));
        }
      },
      fail: (err) => {
        console.warn(`[upload] 请求失败 耗时 ${((Date.now() - startedAt) / 1e3).toFixed(1)}s:`, err.errMsg);
        reject(new Error(err.errMsg || "上传失败"));
      }
    });
  }));
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
