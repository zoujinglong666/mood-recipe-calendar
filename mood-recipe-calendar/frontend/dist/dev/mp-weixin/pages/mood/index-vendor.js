"use strict";
const common_vendor = require("../../common/vendor.js");
const composables_useNavBar = require("../../composables/useNavBar.js");
const api_preferences = require("../../api/preferences.js");
const utils_login = require("../../utils/login.js");
const utils_toast = require("../../utils/toast.js");
const __unplugin_components_0 = () => "../../node-modules/@wot-ui/ui/components/wd-navbar/wd-navbar.js";
if (!Array) {
  const _component_wd_navbar = __unplugin_components_0;
  const _component_layout_default_uni = common_vendor.resolveComponent("layout-default-uni");
  (_component_wd_navbar + _component_layout_default_uni)();
}
if (!Math) {
  MoodPicker();
}
const MoodPicker = () => "../../components/guozai/MoodPicker.js";
const _sfc_main = /* @__PURE__ */ common_vendor.defineComponent({
  __name: "index",
  setup(__props) {
    const router = common_vendor.useRouter();
    const selected = common_vendor.ref("");
    async function onConfirm(m) {
      if (common_vendor.index.getStorageSync("mrc_preference_onboarded")) {
        router.push({ name: "recipe", query: { mood: m.key } });
        return;
      }
      try {
        await utils_login.ensureLogin();
        const preference = await api_preferences.fetchFoodPreference();
        if (preference.onboardingCompleted) {
          common_vendor.index.setStorageSync("mrc_preference_onboarded", "1");
          router.push({ name: "recipe", query: { mood: m.key } });
        } else {
          router.push({ name: "preferences", query: { from: "onboarding", mood: m.key } });
        }
      } catch (e) {
        utils_toast.toastError(e, "暂时无法读取锅仔记忆");
      }
    }
    return (_ctx, _cache) => {
      return {
        a: common_vendor.o(common_vendor.unref(composables_useNavBar.navBack)),
        b: common_vendor.p({
          title: "选一个心情吧",
          ["left-arrow"]: true,
          ["safe-area-inset-top"]: true,
          ["custom-style"]: "background-color: transparent !important;"
        }),
        c: common_vendor.o(onConfirm),
        d: common_vendor.o(($event) => selected.value = $event),
        e: common_vendor.p({
          ["hero-height"]: 360,
          title: "此刻，你的心情是？",
          ["confirm-text"]: "就选它，开始推荐",
          modelValue: selected.value
        })
      };
    };
  }
});
const MiniProgramPage = /* @__PURE__ */ common_vendor._export_sfc(_sfc_main, [["__scopeId", "data-v-900073bc"]]);
exports.MiniProgramPage = MiniProgramPage;
