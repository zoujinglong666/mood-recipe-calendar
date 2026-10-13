"use strict";
const common_vendor = require("../../common/vendor.js");
const utils_assets = require("../../utils/assets.js");
const _sfc_main = /* @__PURE__ */ common_vendor.defineComponent({
  __name: "ErrorState",
  props: {
    text: { default: "网络开小差了" },
    subtext: { default: "检查一下网络再试试吧" },
    actionText: { default: "重新加载" }
  },
  emits: ["retry"],
  setup(__props, { emit: __emit }) {
    const emit = __emit;
    return (_ctx, _cache) => {
      return common_vendor.e({
        a: common_vendor.unref(utils_assets.STATIC_BASE_URL) + "/static/guozai/state_03_error.png",
        b: common_vendor.t(_ctx.text),
        c: _ctx.subtext
      }, _ctx.subtext ? {
        d: common_vendor.t(_ctx.subtext)
      } : {}, {
        e: _ctx.actionText
      }, _ctx.actionText ? {
        f: common_vendor.t(_ctx.actionText),
        g: common_vendor.o(($event) => emit("retry"))
      } : {});
    };
  }
});
const Component = /* @__PURE__ */ common_vendor._export_sfc(_sfc_main, [["__scopeId", "data-v-62bbf91e"]]);
exports.Component = Component;
