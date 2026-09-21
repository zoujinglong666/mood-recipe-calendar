"use strict";
const common_vendor = require("../../../../../common/vendor.js");
if (!Math) {
  wdButton();
}
const wdButton = () => "../wd-button/wd-button.js";
const __default__ = {
  name: "wd-tour",
  options: {
    addGlobalClass: true,
    virtualHost: true,
    styleIsolation: "shared"
  }
};
const _sfc_main = /* @__PURE__ */ common_vendor.defineComponent({
  ...__default__,
  props: common_vendor.tourProps,
  emits: ["update:modelValue", "update:current", "change", "prev", "next", "finish", "skip", "error"],
  setup(__props, { expose: __expose, emit: __emit }) {
    const props = __props;
    const emit = __emit;
    const { translate } = common_vendor.useTranslate("tour");
    const prevText = common_vendor.computed(() => {
      return common_vendor.isDef(props.prevText) ? props.prevText : translate("prev");
    });
    const nextText = common_vendor.computed(() => {
      return common_vendor.isDef(props.nextText) ? props.nextText : translate("next");
    });
    const skipText = common_vendor.computed(() => {
      return common_vendor.isDef(props.skipText) ? props.skipText : translate("skip");
    });
    const finishText = common_vendor.computed(() => {
      return common_vendor.isDef(props.finishText) ? props.finishText : translate("finish");
    });
    const currentIndex = common_vendor.ref(0);
    const elementInfo = common_vendor.ref({
      top: 0,
      left: 0,
      width: 0,
      height: 0
    });
    const windowHeight = common_vendor.ref(0);
    const windowTop = common_vendor.ref(0);
    const isElementInTop = common_vendor.ref(true);
    const lastScrollTop = common_vendor.ref(0);
    const statusBarHeight = common_vendor.ref(0);
    const menuButtonInfo = common_vendor.ref(null);
    const topOffset = common_vendor.ref(0);
    const rootStyle = common_vendor.computed(() => {
      const style = {};
      if (common_vendor.isDef(props.zIndex)) {
        style.zIndex = props.zIndex;
      }
      return common_vendor.objToStyle(style);
    });
    const highlightClass = common_vendor.computed(() => {
      return `wd-tour__highlight ${props.mask ? "wd-tour__highlight--mask" : ""}`;
    });
    const currentStep = common_vendor.computed(() => {
      return props.steps[currentIndex.value] || {};
    });
    function getDefaultStyle() {
      return {
        transition: props.duration + "ms all"
      };
    }
    function calculateHighlightStyle(padding) {
      return {
        transition: props.duration + "ms all,boxShadow 0s,height 0s,width 0s",
        borderRadius: common_vendor.addUnit(props.borderRadius),
        padding: common_vendor.addUnit(padding)
      };
    }
    const highlightStyle = common_vendor.computed(() => {
      if (!elementInfo.value.width && !elementInfo.value.height) {
        return getDefaultStyle();
      }
      const stepPadding = Number(common_vendor.isDef(currentStep.value.padding) ? currentStep.value.padding : props.padding);
      const baseStyle = calculateHighlightStyle(stepPadding);
      const style = {
        ...baseStyle,
        top: common_vendor.addUnit((elementInfo.value.top || 0) - stepPadding),
        left: common_vendor.addUnit((elementInfo.value.left || 0) - stepPadding),
        height: common_vendor.addUnit(elementInfo.value.height || 0),
        width: common_vendor.addUnit(elementInfo.value.width || 0)
      };
      if (common_vendor.isDef(props.mask) && common_vendor.isDef(props.maskColor)) {
        style.boxShadow = `0 0 0 100vh ${props.maskColor}`;
      }
      return common_vendor.objToStyle([{ ...style }, props.highlightStyle]);
    });
    const popoverStyle = common_vendor.computed(() => {
      const style = {};
      if (common_vendor.isDef(props.zIndex)) {
        const zIndex = Number(props.zIndex);
        style.zIndex = zIndex + 1;
        style.transitionDuration = `${props.duration}ms`;
      }
      const stepPadding = Number(common_vendor.isDef(currentStep.value.offset) ? currentStep.value.offset : props.offset);
      const placement = common_vendor.isDef(currentStep.value.placement) ? currentStep.value.placement : "auto";
      const down = placement === "bottom" || placement === "auto" && isElementInTop.value;
      if (down) {
        style.top = common_vendor.addUnit((elementInfo.value.top || 0) + (elementInfo.value.height || 0) + Number(stepPadding));
      } else {
        style.bottom = common_vendor.addUnit(windowHeight.value + windowTop.value - (elementInfo.value.top || 0) + Number(stepPadding));
      }
      return common_vendor.objToStyle(style);
    });
    const highlightElementInfo = common_vendor.computed(() => {
      const stepPadding = Number(common_vendor.isDef(currentStep.value.padding) ? currentStep.value.padding : props.padding);
      if (!elementInfo.value.width && !elementInfo.value.height) {
        return getDefaultStyle();
      }
      const baseStyle = calculateHighlightStyle(stepPadding);
      const style = {
        ...baseStyle,
        top: common_vendor.addUnit((elementInfo.value.top || 0) - stepPadding),
        left: common_vendor.addUnit((elementInfo.value.left || 0) - stepPadding),
        width: common_vendor.addUnit((elementInfo.value.width || 0) + stepPadding * 2),
        height: common_vendor.addUnit((elementInfo.value.height || 0) + stepPadding * 2)
      };
      if (common_vendor.isDef(props.mask) && common_vendor.isDef(props.maskColor)) {
        style.boxShadow = `0 0 0 100vh ${props.maskColor}`;
      }
      return style;
    });
    function noop() {
    }
    async function updateElementInfo() {
      updateSystemInfo();
      const element = currentStep.value.element;
      if (!element)
        return;
      try {
        const res = await common_vendor.getRect(element, false, props.scope);
        initializeElementInfo(res);
        const effectiveBoundaries = getEffectiveBoundaries();
        const scrollNeeds = checkScrollNeeds(res, effectiveBoundaries);
        handleScrolling(res, scrollNeeds, effectiveBoundaries);
        calculateTipPosition(res);
      } catch (error) {
        console.error("updateElementInfo error:", error);
        emit("error", {
          message: "无法找到指定的引导元素",
          element
        });
        if (props.missingStrategy === "skip") {
          handleNext();
        } else if (props.missingStrategy === "hide") {
          emit("update:modelValue", false);
        }
      }
    }
    function updateSystemInfo() {
      const sysInfo = common_vendor.getSystemInfo();
      windowHeight.value = sysInfo.windowHeight;
      windowTop.value = sysInfo.windowTop || 0;
      statusBarHeight.value = sysInfo.statusBarHeight || 0;
    }
    function initializeElementInfo(res) {
      elementInfo.value = res;
      elementInfo.value.top = (res.top || 0) + windowTop.value;
      elementInfo.value.bottom = (res.bottom !== void 0 ? res.bottom : (res.top || 0) + (res.height || 0)) + windowTop.value;
    }
    function getEffectiveBoundaries() {
      let effectiveWindowTop = windowTop.value + Number(topOffset.value);
      let effectiveWindowBottom = windowHeight.value;
      return {
        top: effectiveWindowTop,
        bottom: effectiveWindowBottom
      };
    }
    function checkScrollNeeds(res, boundaries) {
      const needScrollUp = Number(res.top) < boundaries.top;
      const needScrollDown = (res.bottom !== void 0 ? res.bottom : 0) + Number(props.bottomSafetyOffset) > boundaries.bottom;
      return {
        up: needScrollUp,
        //提示框往上走
        down: needScrollDown
        //提示框往下走
      };
    }
    function handleScrolling(res, scrollNeeds, boundaries) {
      if (scrollNeeds.up) {
        scrollUp(res, boundaries);
      } else if (scrollNeeds.down) {
        scrollDown(res);
      }
    }
    function scrollUp(res, boundaries) {
      let scrollDistance = lastScrollTop.value + Number(res.top) - props.padding - boundaries.top;
      elementInfo.value.top = boundaries.top + props.padding;
      elementInfo.value.bottom = windowHeight.value - (boundaries.top + props.padding);
      common_vendor.index.pageScrollTo({
        scrollTop: scrollDistance,
        duration: Number(props.duration),
        success: () => {
          lastScrollTop.value = scrollDistance;
        }
      });
    }
    function scrollDown(res) {
      const bottom = res.bottom || 0;
      let scrollDistance = bottom - windowHeight.value + props.padding + Number(props.bottomSafetyOffset);
      elementInfo.value.top = windowHeight.value - bottom - props.padding - Number(props.bottomSafetyOffset);
      elementInfo.value.bottom = windowHeight.value - props.padding - Number(props.bottomSafetyOffset);
      common_vendor.index.pageScrollTo({
        scrollTop: scrollDistance + lastScrollTop.value,
        duration: Number(props.duration),
        success: () => {
          lastScrollTop.value = scrollDistance + lastScrollTop.value;
        }
      });
    }
    function calculateTipPosition(res) {
      let totalNavHeight = statusBarHeight.value;
      const screenCenter = (windowHeight.value + totalNavHeight) / 2 + windowTop.value;
      const elementCenter = (res.top || 0) + (res.height || 0) / 2 + windowTop.value;
      if (elementCenter < screenCenter) {
        isElementInTop.value = true;
      } else {
        isElementInTop.value = false;
      }
    }
    function handlePrev() {
      if (currentIndex.value > 0) {
        const oldIndex = currentIndex.value;
        currentIndex.value--;
        emit("prev", {
          prevCurrent: oldIndex,
          current: currentIndex.value,
          total: props.steps.length,
          isElementInTop: isElementInTop.value
        });
        emit("change", { current: currentIndex.value });
      }
    }
    function handleNext() {
      if (currentIndex.value < props.steps.length - 1) {
        const oldIndex = currentIndex.value;
        currentIndex.value++;
        emit("next", {
          prevCurrent: oldIndex,
          current: currentIndex.value,
          total: props.steps.length,
          isElementInTop: isElementInTop.value
        });
        emit("change", { current: currentIndex.value });
      } else {
        handleFinish();
      }
    }
    function handleFinish() {
      emit("finish", {
        current: currentIndex.value,
        total: props.steps.length
      });
      currentIndex.value = 0;
      lastScrollTop.value = 0;
      emit("update:modelValue", false);
    }
    function handleSkip() {
      emit("skip", {
        current: currentIndex.value,
        total: props.steps.length
      });
      currentIndex.value = 0;
      lastScrollTop.value = 0;
      emit("update:modelValue", false);
    }
    function handleMask() {
      if (props.clickMaskNext) {
        handleNext();
      }
    }
    common_vendor.watch(
      () => props.current,
      (newVal) => {
        currentIndex.value = newVal;
      }
    );
    const indexRaf = common_vendor.useRaf(updateElementInfo);
    const modelRaf = common_vendor.useRaf(() => {
      updateElementInfo();
      emit("update:current", currentIndex.value);
    });
    common_vendor.watch(
      () => currentIndex.value,
      (newVal) => {
        indexRaf.cancel();
        common_vendor.nextTick$1(() => {
          indexRaf.start();
        });
        emit("update:current", newVal);
      }
    );
    common_vendor.watch(
      () => props.modelValue,
      (newVal) => {
        if (newVal) {
          lastScrollTop.value = 0;
          updateSystemInfo();
          modelRaf.cancel();
          common_vendor.nextTick$1(() => {
            modelRaf.start();
          });
        }
      },
      {
        immediate: true
      }
    );
    if (props.customNav) {
      if (props.topSafetyOffset && Number(props.topSafetyOffset) > 0) {
        topOffset.value = Number(props.topSafetyOffset);
      } else {
        menuButtonInfo.value = common_vendor.index.getMenuButtonBoundingClientRect() || null;
        topOffset.value = menuButtonInfo.value ? menuButtonInfo.value.top : 0;
      }
    } else {
      topOffset.value = Number(props.topSafetyOffset) || 0;
    }
    __expose({
      handlePrev,
      handleNext,
      handleFinish,
      handleSkip
    });
    return (_ctx, _cache) => {
      return common_vendor.e({
        a: _ctx.modelValue
      }, _ctx.modelValue ? common_vendor.e({
        b: common_vendor.n(highlightClass.value),
        c: common_vendor.s(highlightStyle.value),
        d: common_vendor.r("highlight", {
          elementInfo: highlightElementInfo.value
        }),
        e: currentStep.value.content,
        f: _ctx.showTourButtons
      }, _ctx.showTourButtons ? common_vendor.e({
        g: currentIndex.value > 0
      }, currentIndex.value > 0 ? {
        h: common_vendor.t(prevText.value),
        i: common_vendor.p({
          size: "mini",
          variant: "text"
        }),
        j: common_vendor.o(handlePrev)
      } : {}, {
        k: common_vendor.t(skipText.value),
        l: common_vendor.p({
          size: "mini",
          variant: "text"
        }),
        m: common_vendor.o(handleSkip),
        n: currentIndex.value !== _ctx.steps.length - 1
      }, currentIndex.value !== _ctx.steps.length - 1 ? {
        o: common_vendor.t(`${nextText.value}(${currentIndex.value + 1}/${_ctx.steps.length})`),
        p: common_vendor.p({
          size: "mini"
        }),
        q: common_vendor.o(handleNext)
      } : {}, {
        r: currentIndex.value === _ctx.steps.length - 1
      }, currentIndex.value === _ctx.steps.length - 1 ? {
        s: common_vendor.t(finishText.value),
        t: common_vendor.p({
          size: "mini",
          type: "primary"
        }),
        v: common_vendor.o(handleFinish)
      } : {}) : {}, {
        w: common_vendor.s(popoverStyle.value),
        x: common_vendor.o(handleMask),
        y: common_vendor.s(rootStyle.value),
        z: common_vendor.o(noop)
      }) : {});
    };
  }
});
exports._sfc_main = _sfc_main;
