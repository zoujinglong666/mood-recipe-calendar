"use strict";
const common_vendor = require("../../../../../common/vendor.js");
if (!Math) {
  (wdOverlay + wdIcon + wdTransition + wdRootPortal)();
}
const wdIcon = () => "../wd-icon/wd-icon.js";
const wdOverlay = () => "../wd-overlay/wd-overlay.js";
const wdTransition = () => "../wd-transition/wd-transition.js";
const wdRootPortal = () => "../wd-root-portal/wd-root-portal.js";
const __default__ = {
  name: "wd-popup",
  options: {
    virtualHost: true,
    addGlobalClass: true,
    styleIsolation: "shared"
  }
};
const _sfc_main = /* @__PURE__ */ common_vendor.defineComponent({
  ...__default__,
  props: common_vendor.popupProps,
  emits: [
    "update:modelValue",
    "before-enter",
    "enter",
    "before-leave",
    "leave",
    "after-leave",
    "after-enter",
    "click-modal",
    "close"
  ],
  setup(__props, { emit: __emit }) {
    const props = __props;
    const emit = __emit;
    const transitionName = common_vendor.computed(() => {
      if (props.transition) {
        return props.transition;
      }
      if (props.position === "center") {
        return ["zoom-in", "fade"];
      }
      if (props.position === "left") {
        return "slide-left";
      }
      if (props.position === "right") {
        return "slide-right";
      }
      if (props.position === "bottom") {
        return "slide-up";
      }
      if (props.position === "top") {
        return "slide-down";
      }
      return "slide-up";
    });
    const safeBottom = common_vendor.ref(0);
    const style = common_vendor.computed(() => {
      return `z-index:${props.zIndex}; padding-bottom: ${safeBottom.value}px;${props.customStyle}`;
    });
    const rootClass = common_vendor.computed(() => {
      return `wd-popup wd-popup--${props.position} ${!props.transition && props.position === "center" ? "is-deep" : ""} ${props.round ? "is-round" : ""} ${props.customClass || ""}`;
    });
    common_vendor.onBeforeMount(() => {
      if (props.safeAreaInsetBottom) {
        const { safeArea, screenHeight, safeAreaInsets } = common_vendor.getSystemInfo();
        if (safeArea) {
          safeBottom.value = screenHeight - (safeArea.bottom || 0);
        } else {
          safeBottom.value = 0;
        }
      }
    });
    function handleClickModal() {
      emit("click-modal");
      if (props.closeOnClickModal) {
        close();
      }
    }
    function close() {
      emit("close");
      emit("update:modelValue", false);
    }
    function noop() {
    }
    return (_ctx, _cache) => {
      return common_vendor.e({
        a: _ctx.rootPortal
      }, _ctx.rootPortal ? common_vendor.e({
        b: _ctx.modal
      }, _ctx.modal ? {
        c: common_vendor.o(handleClickModal),
        d: common_vendor.o(noop),
        e: common_vendor.p({
          show: _ctx.modelValue,
          ["z-index"]: _ctx.zIndex,
          ["lock-scroll"]: _ctx.lockScroll,
          duration: _ctx.duration,
          ["custom-style"]: _ctx.modalStyle
        })
      } : {}, {
        f: _ctx.closable
      }, _ctx.closable ? {
        g: common_vendor.o(close),
        h: common_vendor.p({
          ["custom-class"]: "wd-popup__close",
          name: "close"
        })
      } : {}, {
        i: common_vendor.o(($event) => emit("before-enter")),
        j: common_vendor.o(($event) => emit("enter")),
        k: common_vendor.o(($event) => emit("after-enter")),
        l: common_vendor.o(($event) => emit("before-leave")),
        m: common_vendor.o(($event) => emit("leave")),
        n: common_vendor.o(($event) => emit("after-leave")),
        o: common_vendor.p({
          ["lazy-render"]: _ctx.lazyRender,
          ["custom-class"]: rootClass.value,
          ["custom-style"]: style.value,
          duration: _ctx.duration,
          show: _ctx.modelValue,
          name: transitionName.value,
          destroy: _ctx.hideWhenClose
        })
      }) : common_vendor.e({
        p: _ctx.modal
      }, _ctx.modal ? {
        q: common_vendor.o(handleClickModal),
        r: common_vendor.o(noop),
        s: common_vendor.p({
          show: _ctx.modelValue,
          ["z-index"]: _ctx.zIndex,
          ["lock-scroll"]: _ctx.lockScroll,
          duration: _ctx.duration,
          ["custom-style"]: _ctx.modalStyle
        })
      } : {}, {
        t: _ctx.closable
      }, _ctx.closable ? {
        v: common_vendor.o(close),
        w: common_vendor.p({
          ["custom-class"]: "wd-popup__close",
          name: "close"
        })
      } : {}, {
        x: common_vendor.o(($event) => emit("before-enter")),
        y: common_vendor.o(($event) => emit("enter")),
        z: common_vendor.o(($event) => emit("after-enter")),
        A: common_vendor.o(($event) => emit("before-leave")),
        B: common_vendor.o(($event) => emit("leave")),
        C: common_vendor.o(($event) => emit("after-leave")),
        D: common_vendor.p({
          ["lazy-render"]: _ctx.lazyRender,
          ["custom-class"]: rootClass.value,
          ["custom-style"]: style.value,
          duration: _ctx.duration,
          show: _ctx.modelValue,
          name: transitionName.value,
          destroy: _ctx.hideWhenClose
        })
      }));
    };
  }
});
exports._sfc_main = _sfc_main;
