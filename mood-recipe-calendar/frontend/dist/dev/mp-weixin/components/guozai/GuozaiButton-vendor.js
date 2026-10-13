"use strict";
const common_vendor = require("../../common/vendor.js");
const _sfc_main = /* @__PURE__ */ common_vendor.defineComponent({
  __name: "GuozaiButton",
  props: {
    variant: { default: "primary" },
    block: { type: Boolean, default: true },
    disabled: { type: Boolean, default: false },
    loading: { type: Boolean, default: false },
    throttle: { type: Boolean, default: true },
    throttleCooldown: { default: 1200 },
    ariaLabel: {}
  },
  emits: ["click"],
  setup(__props, { emit: __emit }) {
    const props = __props;
    const emit = __emit;
    const cooldownUntil = common_vendor.ref(0);
    function onClick() {
      if (props.disabled || props.loading)
        return;
      if (props.throttle && props.throttleCooldown > 0) {
        const now = Date.now();
        if (now < cooldownUntil.value)
          return;
        cooldownUntil.value = now + props.throttleCooldown;
      }
      emit("click");
    }
    return (_ctx, _cache) => {
      return {
        a: common_vendor.t(_ctx.loading ? "处理中…" : ""),
        b: common_vendor.n(`gz-btn--${_ctx.variant}`),
        c: common_vendor.n({
          "gz-btn--block": _ctx.block,
          "is-disabled": _ctx.disabled || _ctx.loading
        }),
        d: _ctx.disabled || _ctx.loading,
        e: _ctx.ariaLabel,
        f: common_vendor.o(onClick)
      };
    };
  }
});
const Component = /* @__PURE__ */ common_vendor._export_sfc(_sfc_main, [["__scopeId", "data-v-1e33db06"]]);
exports.Component = Component;
