"use strict";
const common_vendor = require("../../../../../common/vendor.js");
const __default__ = {
  name: "wd-floating-panel",
  options: {
    virtualHost: true,
    addGlobalClass: true,
    styleIsolation: "shared"
  }
};
const _sfc_main = /* @__PURE__ */ common_vendor.defineComponent({
  ...__default__,
  props: common_vendor.floatingPanelProps,
  emits: ["update:height", "height-change"],
  setup(__props, { emit: __emit }) {
    const touch = common_vendor.useTouch();
    const props = __props;
    const emit = __emit;
    const heightValue = common_vendor.ref(props.height);
    const DAMP = 0.2;
    let startY;
    const windowHeight = common_vendor.ref(0);
    const dragging = common_vendor.ref(false);
    const boundary = common_vendor.computed(() => ({
      min: props.anchors[0] ? props.anchors[0] : 100,
      max: props.anchors[props.anchors.length - 1] ? props.anchors[props.anchors.length - 1] : Math.round(windowHeight.value * 0.6)
    }));
    const anchors = common_vendor.computed(() => props.anchors.length >= 2 ? props.anchors : [boundary.value.min, boundary.value.max]);
    const rootStyle = common_vendor.computed(() => {
      const style = {
        height: common_vendor.addUnit(boundary.value.max),
        transform: `translateY(calc(100% + ${common_vendor.addUnit(-heightValue.value)}))`,
        transition: !dragging.value ? `transform ${props.duration}ms cubic-bezier(0.18, 0.89, 0.32, 1.28)` : "none"
      };
      return `${common_vendor.objToStyle(style)}${props.customStyle}`;
    });
    const updateHeight = (value) => {
      heightValue.value = value;
      emit("update:height", value);
    };
    const handleTouchStart = (event) => {
      touch.touchStart(event);
      dragging.value = true;
      startY = -heightValue.value;
    };
    const handleTouchMove = (event) => {
      const target = event.currentTarget;
      if (target.dataset.id == "content") {
        if (!props.contentDraggable)
          return;
      }
      touch.touchMove(event);
      const moveY = touch.deltaY.value + startY;
      updateHeight(-ease(moveY));
    };
    const handleTouchEnd = () => {
      dragging.value = false;
      updateHeight(common_vendor.closest(anchors.value, heightValue.value));
      if (heightValue.value !== -startY) {
        emit("height-change", { height: heightValue.value });
      }
    };
    const ease = (y) => {
      const absDistance = Math.abs(y);
      const { min, max } = boundary.value;
      if (absDistance > max) {
        return -(max + (absDistance - max) * DAMP);
      }
      if (absDistance < min) {
        return -(min - (min - absDistance) * DAMP);
      }
      return y;
    };
    common_vendor.watch(
      () => props.height,
      (value) => {
        heightValue.value = value;
      }
    );
    common_vendor.watch(
      boundary,
      () => {
        updateHeight(common_vendor.closest(anchors.value, heightValue.value));
      },
      { immediate: true }
    );
    common_vendor.onBeforeMount(() => {
      const { windowHeight: _windowHeight } = common_vendor.getSystemInfo();
      windowHeight.value = _windowHeight;
    });
    return (_ctx, _cache) => {
      return {
        a: common_vendor.n(`wd-floating-panel__header-bar`),
        b: common_vendor.n(`wd-floating-panel__header`),
        c: common_vendor.n(`wd-floating-panel__content`),
        d: _ctx.showScrollbar,
        e: common_vendor.o(handleTouchMove),
        f: common_vendor.n(`wd-floating-panel ${_ctx.customClass} ${_ctx.safeAreaInsetBottom ? "is-safe" : ""}`),
        g: common_vendor.s(rootStyle.value),
        h: common_vendor.o(handleTouchStart),
        i: common_vendor.o(handleTouchMove),
        j: common_vendor.o(handleTouchEnd),
        k: common_vendor.o(handleTouchEnd)
      };
    };
  }
});
exports._sfc_main = _sfc_main;
