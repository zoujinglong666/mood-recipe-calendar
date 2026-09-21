"use strict";
const common_vendor = require("../../../../../common/vendor.js");
if (!Math) {
  wdTransition();
}
const wdTransition = () => "../wd-transition/wd-transition.js";
const __default__ = {
  name: "wd-overlay",
  options: {
    virtualHost: true,
    addGlobalClass: true,
    styleIsolation: "shared"
  }
};
const _sfc_main = /* @__PURE__ */ common_vendor.defineComponent({
  ...__default__,
  props: common_vendor.overlayProps,
  emits: ["click", "before-enter", "enter", "after-enter", "before-leave", "leave", "after-leave"],
  setup(__props, { emit: __emit }) {
    const emit = __emit;
    function handleClick() {
      emit("click");
    }
    return (_ctx, _cache) => {
      return {
        a: common_vendor.o(($event) => emit("before-enter")),
        b: common_vendor.o(($event) => emit("enter")),
        c: common_vendor.o(($event) => emit("after-enter")),
        d: common_vendor.o(($event) => emit("before-leave")),
        e: common_vendor.o(($event) => emit("leave")),
        f: common_vendor.o(($event) => emit("after-leave")),
        g: common_vendor.o(handleClick),
        h: common_vendor.p({
          show: _ctx.show,
          name: "fade",
          ["custom-class"]: `wd-overlay ${_ctx.customClass}`,
          duration: _ctx.duration,
          ["custom-style"]: `z-index: ${_ctx.zIndex}; ${_ctx.customStyle}`,
          ["disable-touch-move"]: _ctx.lockScroll
        })
      };
    };
  }
});
exports._sfc_main = _sfc_main;
