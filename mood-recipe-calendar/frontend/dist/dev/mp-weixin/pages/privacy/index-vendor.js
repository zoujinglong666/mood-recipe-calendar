"use strict";
const common_vendor = require("../../common/vendor.js");
const composables_useNavBar = require("../../composables/useNavBar.js");
const utils_assets = require("../../utils/assets.js");
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
const updatedAt = "2026 年 9 月 17 日";
const _sfc_main = /* @__PURE__ */ common_vendor.defineComponent({
  __name: "index",
  setup(__props) {
    const router = common_vendor.useRouter();
    return (_ctx, _cache) => {
      return {
        a: common_vendor.o(common_vendor.unref(composables_useNavBar.navBack)),
        b: common_vendor.p({
          title: "隐私政策",
          ["left-arrow"]: true,
          ["safe-area-inset-top"]: true,
          ["custom-style"]: "background-color: transparent !important;"
        }),
        c: common_vendor.t(updatedAt),
        d: `${common_vendor.unref(utils_assets.STATIC_BASE_URL)}/static/guozai/action_06_glasses.png`,
        e: common_vendor.p({
          name: "heart",
          size: 40,
          color: "#EF5A3C"
        }),
        f: common_vendor.p({
          name: "book",
          size: 40,
          color: "#EF5A3C"
        }),
        g: common_vendor.p({
          name: "user",
          size: 40,
          color: "#EF5A3C"
        }),
        h: common_vendor.p({
          name: "user",
          size: 40,
          color: "#EF5A3C"
        }),
        i: common_vendor.p({
          name: "heart",
          size: 40,
          color: "#EF5A3C"
        }),
        j: common_vendor.o(($event) => common_vendor.unref(router).push({
          name: "feedback"
        }))
      };
    };
  }
});
const MiniProgramPage = /* @__PURE__ */ common_vendor._export_sfc(_sfc_main, [["__scopeId", "data-v-26151888"]]);
exports.MiniProgramPage = MiniProgramPage;
