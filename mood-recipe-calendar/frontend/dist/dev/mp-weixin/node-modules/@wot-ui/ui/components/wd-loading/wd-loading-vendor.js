"use strict";
const common_vendor = require("../../../../../common/vendor.js");
const __default__ = {
  name: "wd-loading",
  options: {
    virtualHost: true,
    addGlobalClass: true,
    styleIsolation: "shared"
  }
};
const _sfc_main = /* @__PURE__ */ common_vendor.defineComponent({
  ...__default__,
  props: common_vendor.loadingProps,
  setup(__props) {
    const props = __props;
    const dots = Array.from({ length: 12 }, (_, i) => i);
    const rootStyle = common_vendor.computed(() => {
      const style = {};
      if (common_vendor.isDef(props.color)) {
        style.color = props.color;
      }
      if (props.inheritColor) {
        style.color = "inherit";
      }
      return `${common_vendor.objToStyle(style)} ${props.customStyle}`;
    });
    const spinnerStyle = common_vendor.computed(() => {
      const style = {};
      if (common_vendor.isDef(props.size)) {
        style.height = common_vendor.addUnit(props.size);
        style.width = common_vendor.addUnit(props.size);
      }
      return common_vendor.objToStyle(style);
    });
    return (_ctx, _cache) => {
      return common_vendor.e({
        a: _ctx.type === "circular"
      }, _ctx.type === "circular" ? {} : {}, {
        b: _ctx.type === "spinner"
      }, _ctx.type === "spinner" ? {
        c: common_vendor.f(common_vendor.unref(dots), (i, k0, i0) => {
          return {
            a: i
          };
        })
      } : _ctx.type === "dots" ? {
        e: common_vendor.f(3, (i, k0, i0) => {
          return {
            a: i
          };
        })
      } : _ctx.type === "wave" ? {
        g: common_vendor.f(4, (i, k0, i0) => {
          return {
            a: i
          };
        })
      } : {}, {
        d: _ctx.type === "dots",
        f: _ctx.type === "wave",
        h: common_vendor.n(`wd-loading__spinner wd-loading__spinner--${_ctx.type} ${_ctx.customSpinnerClass}`),
        i: common_vendor.s(spinnerStyle.value),
        j: _ctx.$slots.default || _ctx.text
      }, _ctx.$slots.default || _ctx.text ? {
        k: common_vendor.t(_ctx.text)
      } : {}, {
        l: common_vendor.n(`wd-loading wd-loading--${_ctx.direction} ${_ctx.customClass}`),
        m: common_vendor.s(rootStyle.value)
      });
    };
  }
});
exports._sfc_main = _sfc_main;
