"use strict";
const common_vendor = require("../../../../../common/vendor.js");
const __default__ = {
  name: "wd-gap",
  options: {
    addGlobalClass: true,
    virtualHost: true,
    styleIsolation: "shared"
  }
};
const _sfc_main = /* @__PURE__ */ common_vendor.defineComponent({
  ...__default__,
  props: common_vendor.gapProps,
  setup(__props) {
    const props = __props;
    const rootStyle = common_vendor.computed(() => {
      const rootStyle2 = {};
      if (common_vendor.isDef(props.bgColor)) {
        rootStyle2["background"] = props.bgColor;
      }
      if (common_vendor.isDef(props.height)) {
        rootStyle2["height"] = common_vendor.addUnit(props.height);
      }
      return `${common_vendor.objToStyle(rootStyle2)}${props.customStyle}`;
    });
    return (_ctx, _cache) => {
      return {
        a: common_vendor.n(`wd-gap ${_ctx.safeAreaBottom ? "wd-gap--safe" : ""} ${_ctx.customClass}`),
        b: common_vendor.s(rootStyle.value)
      };
    };
  }
});
exports._sfc_main = _sfc_main;
