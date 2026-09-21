"use strict";
const common_vendor = require("../../common/vendor.js");
const utils_assets = require("../../utils/assets.js");
const composables_useNavBar = require("../../composables/useNavBar.js");
const api_records = require("../../api/records.js");
const stores_user = require("../../stores/user.js");
const __unplugin_components_0 = () => "../../node-modules/@wot-ui/ui/components/wd-navbar/wd-navbar.js";
if (!Array) {
  const _component_wd_navbar = __unplugin_components_0;
  const _component_layout_default_uni = common_vendor.resolveComponent("layout-default-uni");
  (_component_wd_navbar + _component_layout_default_uni)();
}
if (!Math) {
  Icon();
}
const Icon = () => "../../components/common/Icon.js";
const _sfc_main = /* @__PURE__ */ common_vendor.defineComponent({
  __name: "index",
  setup(__props) {
    const userStore = stores_user.useUserStore();
    const FALLBACK_QUOTES = [
      "好好吃饭，是头等大事",
      "吃饭最大，烦恼靠后",
      "人间烟火，最抚人心",
      "一餐一饭，皆是温柔",
      "吃好每顿，过好每天",
      "胃暖了，心就暖了",
      "认真吃饭，就是爱自己",
      "美食在前，万事可期",
      "吃饱喝足，继续出发",
      "今日份开心，从吃饭开始",
      "生活再忙，也要好好吃饭",
      "一碗热饭，治愈一切"
    ];
    const heroQuote = common_vendor.ref(FALLBACK_QUOTES[Math.floor(Math.random() * FALLBACK_QUOTES.length)]);
    common_vendor.onMounted(async () => {
      if (!userStore.isLoggedIn)
        return;
      try {
        const hour = (/* @__PURE__ */ new Date()).getHours();
        const msg = await api_records.fetchCompanionMessage(hour);
        const text = (msg.message || msg.greeting || "").replace(/[。！！\s]/g, "");
        if (text && text.length <= 14) {
          heroQuote.value = text.length > 12 ? text.slice(0, 12) : text;
        }
      } catch {
      }
    });
    return (_ctx, _cache) => {
      return {
        a: common_vendor.o(common_vendor.unref(composables_useNavBar.navBack)),
        b: common_vendor.p({
          title: "关于锅仔",
          ["left-arrow"]: true,
          ["safe-area-inset-top"]: true,
          ["custom-style"]: "background-color: transparent !important;"
        }),
        c: common_vendor.t(heroQuote.value),
        d: common_vendor.unref(utils_assets.STATIC_BASE_URL) + "/static/guozai/action_06_glasses.png",
        e: common_vendor.p({
          name: "heart",
          size: 36,
          color: "#FF6B5B"
        }),
        f: common_vendor.p({
          name: "book",
          size: 36,
          color: "#FF6B5B"
        }),
        g: common_vendor.p({
          name: "gear",
          size: 36,
          color: "#FF6B5B"
        }),
        h: common_vendor.unref(utils_assets.STATIC_BASE_URL) + "/static/guozai/action_08_peek.png"
      };
    };
  }
});
const MiniProgramPage = /* @__PURE__ */ common_vendor._export_sfc(_sfc_main, [["__scopeId", "data-v-6b4e7e2d"]]);
exports.MiniProgramPage = MiniProgramPage;
