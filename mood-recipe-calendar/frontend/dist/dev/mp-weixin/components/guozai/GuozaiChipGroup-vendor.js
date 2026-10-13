"use strict";
const common_vendor = require("../../common/vendor.js");
const _sfc_main = /* @__PURE__ */ common_vendor.defineComponent({
  __name: "GuozaiChipGroup",
  props: {
    modelValue: { default: "" },
    options: {},
    multiple: { type: Boolean, default: false },
    columns: { default: 0 },
    disabled: { type: Boolean, default: false },
    ariaLabel: { default: "选项" }
  },
  emits: ["update:modelValue", "change"],
  setup(__props, { emit: __emit }) {
    const props = __props;
    const emit = __emit;
    const selected = common_vendor.computed(() => props.multiple ? Array.isArray(props.modelValue) ? props.modelValue : [] : [String(props.modelValue ?? "")]);
    function isSelected(value) {
      return selected.value.includes(value);
    }
    function toggle(value) {
      if (props.disabled)
        return;
      if (props.multiple) {
        const current = Array.isArray(props.modelValue) ? props.modelValue : [];
        const next = current.includes(value) ? current.filter((item) => item !== value) : [...current, value];
        emit("update:modelValue", next);
        emit("change", next);
        return;
      }
      emit("update:modelValue", value);
      emit("change", value);
    }
    return (_ctx, _cache) => {
      return {
        a: common_vendor.f(_ctx.options, (option, k0, i0) => {
          return {
            a: common_vendor.t(option.label),
            b: option.value,
            c: isSelected(option.value) ? 1 : "",
            d: isSelected(option.value),
            e: option.label,
            f: common_vendor.o(($event) => toggle(option.value), option.value)
          };
        }),
        b: _ctx.disabled ? 1 : "",
        c: _ctx.multiple ? "checkbox" : "radio",
        d: _ctx.columns > 0 ? 1 : "",
        e: common_vendor.s(_ctx.columns > 0 ? {
          gridTemplateColumns: `repeat(${_ctx.columns}, 1fr)`
        } : {}),
        f: _ctx.ariaLabel
      };
    };
  }
});
const Component = /* @__PURE__ */ common_vendor._export_sfc(_sfc_main, [["__scopeId", "data-v-174e7958"]]);
exports.Component = Component;
