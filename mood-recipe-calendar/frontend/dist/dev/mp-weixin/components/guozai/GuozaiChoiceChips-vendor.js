"use strict";
const common_vendor = require("../../common/vendor.js");
const _sfc_main = /* @__PURE__ */ common_vendor.defineComponent({
  __name: "GuozaiChoiceChips",
  props: {
    modelValue: { default: () => [] },
    options: {},
    disabled: { type: Boolean, default: false },
    ariaLabel: { default: "做饭反馈，可多选" }
  },
  emits: ["update:modelValue", "select"],
  setup(__props, { emit: __emit }) {
    const props = __props;
    const emit = __emit;
    function toggle(value) {
      if (props.disabled)
        return;
      const next = props.modelValue.includes(value) ? props.modelValue.filter((item) => item !== value) : [...props.modelValue, value];
      emit("update:modelValue", next);
      emit("select", value);
    }
    return (_ctx, _cache) => {
      return {
        a: common_vendor.f(_ctx.options, (option, k0, i0) => {
          return {
            a: common_vendor.t(_ctx.modelValue.includes(option.value) ? "✓" : "+"),
            b: common_vendor.t(option.label),
            c: option.value,
            d: _ctx.modelValue.includes(option.value) ? 1 : "",
            e: _ctx.modelValue.includes(option.value),
            f: option.label,
            g: common_vendor.o(($event) => toggle(option.value), option.value)
          };
        }),
        b: _ctx.disabled ? 1 : "",
        c: _ctx.ariaLabel
      };
    };
  }
});
const Component = /* @__PURE__ */ common_vendor._export_sfc(_sfc_main, [["__scopeId", "data-v-56cdbac0"]]);
exports.Component = Component;
