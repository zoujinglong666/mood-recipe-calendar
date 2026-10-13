"use strict";
const common_vendor = require("../../../../../common/vendor.js");
const __default__ = {
  name: "wd-icon",
  options: {
    virtualHost: true,
    externalClasses: ["custom-class"],
    addGlobalClass: true,
    styleIsolation: "shared"
  }
};
const _sfc_main = /* @__PURE__ */ common_vendor.defineComponent({
  ...__default__,
  props: common_vendor.iconProps,
  emits: ["click", "touch"],
  setup(__props, { emit: __emit }) {
    const props = __props;
    const emit = __emit;
    const isImage = common_vendor.computed(() => {
      return !props.cssIcon && common_vendor.isDef(props.name) && props.name.includes("/");
    });
    const rootClass = common_vendor.computed(() => {
      const clazz = {
        "wd-icon": true
      };
      if (props.cssIcon) {
        clazz["wd-icon--css"] = true;
        if (typeof props.cssIcon === "string") {
          clazz[props.cssIcon] = true;
        } else {
          clazz[props.name] = true;
        }
      } else if (isImage.value) {
        clazz["wd-icon--image"] = true;
      } else {
        clazz[props.classPrefix] = true;
        clazz[`${props.classPrefix}-${props.name}`] = true;
      }
      return common_vendor.normalizeClass([clazz, props.customClass]);
    });
    const rootStyle = common_vendor.computed(() => {
      const style = {};
      if (props.color) {
        style["color"] = props.color;
      }
      if (props.size) {
        const sizeValue = common_vendor.addUnit(props.size);
        style["font-size"] = sizeValue;
        if (props.cssIcon || isImage.value) {
          style["width"] = sizeValue;
          style["height"] = sizeValue;
        }
      }
      return `${common_vendor.objToStyle(style)} ${props.customStyle}`;
    });
    function handleClick(event) {
      emit("click", event);
    }
    return (_ctx, _cache) => {
      return common_vendor.e({
        a: isImage.value
      }, isImage.value ? {
        b: _ctx.name
      } : {}, {
        c: common_vendor.n(rootClass.value),
        d: common_vendor.s(rootStyle.value),
        e: common_vendor.o(handleClick)
      });
    };
  }
});
exports._sfc_main = _sfc_main;
