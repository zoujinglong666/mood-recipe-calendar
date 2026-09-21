"use strict";
const common_vendor = require("../../../../../common/vendor.js");
const __default__ = {
  name: "wd-radio-group",
  options: {
    virtualHost: true,
    addGlobalClass: true,
    styleIsolation: "shared"
  }
};
const _sfc_main = /* @__PURE__ */ common_vendor.defineComponent({
  ...__default__,
  props: common_vendor.radioGroupProps,
  emits: ["change", "update:modelValue"],
  setup(__props, { emit: __emit }) {
    const props = __props;
    const emit = __emit;
    const { linkChildren } = common_vendor.useChildren(common_vendor.RADIO_GROUP_KEY);
    linkChildren({ props, updateValue });
    common_vendor.watch(
      () => props.type,
      (newValue) => {
        const type = ["circle", "dot", "button", "square"];
        if (type.indexOf(newValue) === -1)
          console.error(`type must be one of ${type.toString()}`);
      },
      { deep: true, immediate: true }
    );
    function updateValue(value) {
      emit("update:modelValue", value);
      emit("change", {
        value
      });
    }
    return (_ctx, _cache) => {
      return {
        a: common_vendor.n(`wd-radio-group  ${_ctx.customClass}`),
        b: common_vendor.s(_ctx.customStyle)
      };
    };
  }
});
exports._sfc_main = _sfc_main;
