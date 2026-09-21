"use strict";
const common_vendor = require("../../common/vendor.js");
const utils_assets = require("../../utils/assets.js");
const stores_user = require("../../stores/user.js");
const utils_authRecovery = require("../../utils/authRecovery.js");
const utils_login = require("../../utils/login.js");
const utils_toast = require("../../utils/toast.js");
if (!Array) {
  const _component_layout_default_uni = common_vendor.resolveComponent("layout-default-uni");
  _component_layout_default_uni();
}
const _sfc_main = /* @__PURE__ */ common_vendor.defineComponent({
  __name: "index",
  setup(__props) {
    const router = common_vendor.useRouter();
    const userStore = stores_user.useUserStore();
    const loggingIn = common_vendor.ref(false);
    const canWechatLogin = common_vendor.ref(false);
    canWechatLogin.value = true;
    async function login() {
      if (!canWechatLogin.value || loggingIn.value)
        return;
      loggingIn.value = true;
      try {
        userStore.clearLogoutFlag();
        await utils_login.ensureLogin();
        const target = utils_authRecovery.consumeAuthReturn();
        if (target)
          common_vendor.index.reLaunch({ url: target });
        else
          router.pushTab({ name: "profile" });
      } catch (error) {
        utils_toast.toastError(error, "登录失败，请重试");
      } finally {
        loggingIn.value = false;
      }
    }
    return (_ctx, _cache) => {
      return {
        a: common_vendor.unref(utils_assets.STATIC_BASE_URL) + "/static/guozai/action_09_celebrate.png",
        b: common_vendor.t(loggingIn.value ? "锅仔正在连接微信…" : "微信一键登录"),
        c: loggingIn.value,
        d: common_vendor.o(login)
      };
    };
  }
});
const MiniProgramPage = /* @__PURE__ */ common_vendor._export_sfc(_sfc_main, [["__scopeId", "data-v-45258083"]]);
exports.MiniProgramPage = MiniProgramPage;
