"use strict";
const common_vendor = require("../../common/vendor.js");
const utils_assets = require("../../utils/assets.js");
const _sfc_main = /* @__PURE__ */ common_vendor.defineComponent({
  __name: "SuccessModal",
  props: {
    visible: { type: Boolean },
    title: { default: "记录成功！" },
    subtitle: { default: "今天也好好吃饭了呢" },
    confirmText: { default: "好的" }
  },
  emits: ["confirm"],
  setup(__props, { emit: __emit }) {
    const emit = __emit;
    function handleConfirm() {
      emit("confirm");
    }
    return (_ctx, _cache) => {
      return common_vendor.e({
        a: _ctx.visible
      }, _ctx.visible ? {
        b: `${common_vendor.unref(utils_assets.STATIC_BASE_URL)}/static/guozai/action_09_celebrate.png`,
        c: common_vendor.t(_ctx.title),
        d: common_vendor.t(_ctx.subtitle),
        e: common_vendor.t(_ctx.confirmText),
        f: common_vendor.o(handleConfirm),
        g: common_vendor.o(() => {
        }),
        h: common_vendor.o(handleConfirm)
      } : {});
    };
  }
});
const Component = /* @__PURE__ */ common_vendor._export_sfc(_sfc_main, [["__scopeId", "data-v-ca257a88"]]);
exports.Component = Component;
