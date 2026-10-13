"use strict";
const common_vendor = require("../../../../../common/vendor.js");
if (!Math) {
  wdIcon();
}
const wdIcon = () => "../wd-icon/wd-icon.js";
const _sfc_main = /* @__PURE__ */ common_vendor.defineComponent({
  __name: "wd-swiper-nav",
  props: common_vendor.swiperNavprops,
  emits: ["change"],
  setup(__props, { emit: __emit }) {
    const emit = __emit;
    function handleNav(dir) {
      const source = "nav";
      emit("change", { dir, source });
    }
    return (_ctx, _cache) => {
      return common_vendor.e({
        a: _ctx.showControls
      }, _ctx.showControls ? {
        b: common_vendor.p({
          name: "left",
          ["custom-class"]: "wd-swiper-nav__btn-icon"
        }),
        c: common_vendor.o(($event) => handleNav("prev")),
        d: common_vendor.p({
          name: "right",
          ["custom-class"]: "wd-swiper-nav__btn-icon"
        }),
        e: common_vendor.o(($event) => handleNav("next"))
      } : {}, {
        f: _ctx.total >= _ctx.minShowNum
      }, _ctx.total >= _ctx.minShowNum ? common_vendor.e({
        g: _ctx.type === "dots" || _ctx.type === "dots-bar"
      }, _ctx.type === "dots" || _ctx.type === "dots-bar" ? {
        h: common_vendor.f(_ctx.total, (_, index, i0) => {
          return {
            a: index,
            b: common_vendor.n(`wd-swiper-nav__item--${_ctx.type} ${_ctx.current === index ? "is-active" : ""} is-${_ctx.direction}`)
          };
        })
      } : {}, {
        i: _ctx.type === "fraction"
      }, _ctx.type === "fraction" ? {
        j: common_vendor.t(_ctx.current + 1),
        k: common_vendor.t(_ctx.total)
      } : {}, {
        l: common_vendor.s(_ctx.customStyle),
        m: common_vendor.n(`wd-swiper-nav wd-swiper-nav--${_ctx.direction} wd-swiper-nav--${_ctx.type} wd-swiper-nav--${_ctx.indicatorPosition} ${_ctx.customClass}`)
      }) : {});
    };
  }
});
exports._sfc_main = _sfc_main;
