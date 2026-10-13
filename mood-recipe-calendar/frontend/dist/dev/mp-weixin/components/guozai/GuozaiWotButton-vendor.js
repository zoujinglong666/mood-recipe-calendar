"use strict";
const common_vendor = require("../../common/vendor.js");
const __unplugin_components_0 = () => "../../node-modules/@wot-ui/ui/components/wd-button/wd-button.js";
if (!Array) {
  const _component_wd_button = __unplugin_components_0;
  _component_wd_button();
}
const GRADIENT = "linear-gradient(135deg, #FF9E6B 0%, #F2542D 100%)";
const GRADIENT_PRESSED = "linear-gradient(135deg, #F58A50 0%, #E24A22 100%)";
const _sfc_main = /* @__PURE__ */ common_vendor.defineComponent({
  __name: "GuozaiWotButton",
  props: {
    type: { default: "primary" },
    variant: { default: "base" },
    size: { default: "medium" },
    block: { type: Boolean, default: false },
    round: { type: Boolean, default: false },
    hairline: { type: Boolean, default: false },
    disabled: { type: Boolean, default: false },
    loading: { type: Boolean, default: false },
    icon: { default: "" },
    openType: { default: "" },
    throttle: { type: Boolean, default: true },
    throttleCooldown: { default: 1200 }
  },
  emits: ["click"],
  setup(__props, { emit: __emit }) {
    const props = __props;
    const emit = __emit;
    const pressed = common_vendor.ref(false);
    const brandVars = {
      "--wot-button-primary-bg": GRADIENT,
      "--wot-button-primary-bg-active": GRADIENT_PRESSED,
      "--wot-button-primary-color": "#fff",
      "--wot-button-primary-color-active": "#fff",
      "--wot-button-primary-plain-color": "#EF5A3C",
      "--wot-button-primary-plain-border": "#EF5A3C",
      "--wot-button-primary-soft-bg": "#FCDCCB",
      "--wot-button-primary-soft-color": "#C63F22",
      "--wot-button-danger-bg": "#C0301F",
      "--wot-button-danger-color": "#fff",
      "--wot-button-radius-full": "999rpx"
    };
    const customStyle = common_vendor.computed(() => {
      const parts = [];
      if (props.type === "primary") {
        if (props.variant === "base") {
          parts.push(`background: ${pressed.value ? GRADIENT_PRESSED : GRADIENT}`);
          parts.push("color: #fff");
        } else if (props.variant === "soft") {
          parts.push("background: #FCDCCB");
          parts.push("color: #C63F22");
        } else {
          parts.push("color: #EF5A3C");
        }
      } else if (props.type === "danger") {
        parts.push("background: #C0301F");
        parts.push("color: #fff");
      }
      return parts.join(";");
    });
    const openTypeValue = common_vendor.computed(() => props.openType || void 0);
    const cooldownUntil = common_vendor.ref(0);
    function onClick(event) {
      if (props.disabled || props.loading)
        return;
      if (props.throttle && props.throttleCooldown > 0) {
        const now = Date.now();
        if (now < cooldownUntil.value)
          return;
        cooldownUntil.value = now + props.throttleCooldown;
      }
      emit("click", event);
    }
    return (_ctx, _cache) => {
      return {
        a: common_vendor.s(brandVars),
        b: common_vendor.o(($event) => pressed.value = true),
        c: common_vendor.o(($event) => pressed.value = false),
        d: common_vendor.o(($event) => pressed.value = false),
        e: common_vendor.o(onClick),
        f: common_vendor.p({
          ["custom-style"]: customStyle.value,
          type: _ctx.type,
          variant: _ctx.variant,
          size: _ctx.size,
          block: _ctx.block,
          round: _ctx.round,
          hairline: _ctx.hairline,
          disabled: _ctx.disabled,
          loading: _ctx.loading,
          icon: _ctx.icon || void 0,
          ["open-type"]: openTypeValue.value
        })
      };
    };
  }
});
exports._sfc_main = _sfc_main;
