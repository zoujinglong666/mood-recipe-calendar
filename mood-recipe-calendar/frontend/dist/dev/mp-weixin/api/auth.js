"use strict";
Object.defineProperty(exports, Symbol.toStringTag, { value: "Module" });
const api_request = require("./request.js");
function login(code) {
  return api_request.post("/auth/login", { code });
}
function getUserInfo(_openid) {
  return api_request.get("/auth/user");
}
function updateUserInfo(data) {
  const { openid: _openid, ...request } = data;
  return api_request.put("/auth/user", request);
}
function logout() {
  return api_request.post("/auth/logout");
}
function deleteAccount() {
  return api_request.del("/auth/account", { confirmed: true });
}
function getAgentMemory() {
  return api_request.get("/agent/memory");
}
function forgetAgentMemory(key) {
  return api_request.del(`/agent/memory/${encodeURIComponent(key)}`);
}
function clearAgentMemory() {
  return api_request.del("/agent/memory");
}
function setAgentPersonalization(enabled) {
  return api_request.put("/agent/memory/personalization", { enabled });
}
exports.clearAgentMemory = clearAgentMemory;
exports.deleteAccount = deleteAccount;
exports.forgetAgentMemory = forgetAgentMemory;
exports.getAgentMemory = getAgentMemory;
exports.getUserInfo = getUserInfo;
exports.login = login;
exports.logout = logout;
exports.setAgentPersonalization = setAgentPersonalization;
exports.updateUserInfo = updateUserInfo;
