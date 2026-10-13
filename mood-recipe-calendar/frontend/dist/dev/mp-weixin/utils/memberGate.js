"use strict";
const common_vendor = require("../common/vendor.js");
const config_memberGates = require("../config/memberGates.js");
const stores_user = require("../stores/user.js");
function requireMember(key, opts) {
  const userStore = stores_user.useUserStore();
  if (userStore.isActiveMember)
    return true;
  const gate = config_memberGates.MEMBER_GATES[key];
  common_vendor.index.showModal({
    title: gate.title,
    content: gate.content,
    confirmText: "去开通",
    cancelText: "暂不",
    confirmColor: "#EF5A3C",
    success: (res) => {
      if (res.confirm) {
        common_vendor.index.navigateTo({ url: "/pages/membership/index" });
      }
    }
  });
  return false;
}
exports.requireMember = requireMember;
