"use strict";
const common_vendor = require("../../common/vendor.js");
const utils_assets = require("../../utils/assets.js");
const _sfc_main = /* @__PURE__ */ common_vendor.defineComponent({
  __name: "EmptyState",
  props: {
    text: { default: "这里还有点空" },
    subtext: { default: "去记录点什么吧" },
    actionText: { default: "" }
  },
  emits: ["action"],
  setup(__props, { emit: __emit }) {
    const emit = __emit;
    return (_ctx, _cache) => {
      return common_vendor.e({
        a: common_vendor.unref(utils_assets.STATIC_BASE_URL) + "/static/guozai/state_01_empty.png",
        b: common_vendor.t(_ctx.text),
        c: _ctx.subtext
      }, _ctx.subtext ? {
        d: common_vendor.t(_ctx.subtext)
      } : {}, {
        e: _ctx.actionText
      }, _ctx.actionText ? {
        f: common_vendor.t(_ctx.actionText),
        g: common_vendor.o(($event) => emit("action"))
      } : {});
    };
  }
});
const Component = /* @__PURE__ */ common_vendor._export_sfc(_sfc_main, [["__scopeId", "data-v-90b99d3a"]]);
exports.Component = Component;
