"use strict";
const common_vendor = require("../../common/vendor.js");
const utils_assets = require("../../utils/assets.js");
const _sfc_main = /* @__PURE__ */ common_vendor.defineComponent({
  __name: "GuozaiInsightCard",
  props: {
    title: { default: "锅仔记住了" },
    items: { default: () => [] },
    variant: { default: "learned" }
  },
  setup(__props) {
    const props = __props;
    const image = common_vendor.computed(() => props.variant === "recommendation" ? `${utils_assets.STATIC_BASE_URL}/static/guozai/action_10_thinking.png` : `${utils_assets.STATIC_BASE_URL}/static/guozai/action_09_celebrate.png`);
    return (_ctx, _cache) => {
      return common_vendor.e({
        a: common_vendor.unref(image),
        b: common_vendor.t(_ctx.variant === "recommendation" ? "这次真的用上了" : "这一餐之后"),
        c: common_vendor.t(_ctx.title),
        d: _ctx.items.length
      }, _ctx.items.length ? {
        e: common_vendor.f(_ctx.items, (item, k0, i0) => {
          return {
            a: common_vendor.t(item.text),
            b: `${item.type}-${item.memoryKey || item.text}`
          };
        })
      } : {}, {
        f: common_vendor.n(`guozai-insight--${_ctx.variant}`)
      });
    };
  }
});
const Component = /* @__PURE__ */ common_vendor._export_sfc(_sfc_main, [["__scopeId", "data-v-51d3ff7e"]]);
exports.Component = Component;
