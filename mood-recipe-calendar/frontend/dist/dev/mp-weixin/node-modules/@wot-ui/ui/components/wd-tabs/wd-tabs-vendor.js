"use strict";
const common_vendor = require("../../../../../common/vendor.js");
if (!Math) {
  (wdBadge + wdIcon + wdSticky + wdStickyBox)();
}
const wdIcon = () => "../wd-icon/wd-icon.js";
const wdBadge = () => "../wd-badge/wd-badge.js";
const wdSticky = () => "../wd-sticky/wd-sticky.js";
const wdStickyBox = () => "../wd-sticky-box/wd-sticky-box.js";
const __default__ = {
  name: "wd-tabs",
  options: {
    addGlobalClass: true,
    virtualHost: true,
    styleIsolation: "shared"
  }
};
const _sfc_main = /* @__PURE__ */ common_vendor.defineComponent({
  ...__default__,
  props: common_vendor.tabsProps,
  emits: ["change", "disabled", "click", "update:modelValue"],
  setup(__props, { expose: __expose, emit: __emit }) {
    const $item = ".wd-tabs__nav-item";
    const $itemText = ".wd-tabs__nav-item-text";
    const $container = ".wd-tabs__nav-container";
    const props = __props;
    const emit = __emit;
    const { translate } = common_vendor.useTranslate("tabs");
    const state = common_vendor.reactive({
      /** 选中值的索引，默认第一个 */
      activeIndex: 0,
      /** 激活项边框线样式 */
      lineStyle: "display:none;",
      /** 是否使用内部激活项边框线，当外部激活下划线未成功渲染时显示内部定位的 */
      useInnerLine: false,
      /** 是否初始化 */
      inited: false,
      /** 是否动画中 */
      animating: false,
      /** map的开关 */
      mapShow: false,
      /** scroll-view偏移量 */
      scrollLeft: 0
    });
    const { children, linkChildren } = common_vendor.useChildren(common_vendor.TABS_KEY);
    linkChildren({ state, props });
    const { proxy } = common_vendor.getCurrentInstance();
    const touch = common_vendor.useTouch();
    const innerSlidable = common_vendor.computed(() => {
      return props.slidable === "always" || children.length > props.slidableNum;
    });
    const mapHeaderStyle = common_vendor.computed(() => {
      const style = {};
      if (!state.mapShow) {
        style.display = "none";
      }
      if (state.animating) {
        style.opacity = 1;
      }
      return common_vendor.objToStyle(style);
    });
    const bodyStyle = common_vendor.computed(() => {
      if (!props.animated) {
        return "";
      }
      return common_vendor.objToStyle({
        left: -100 * state.activeIndex + "%",
        "transition-duration": props.duration + "ms",
        "-webkit-transition-duration": props.duration + "ms"
      });
    });
    const getTabName = (tab, index) => {
      return common_vendor.isDef(tab.name) ? tab.name : index;
    };
    const getTabItemStyle = (index) => {
      const { color, inactiveColor } = props;
      const style = {};
      if (state.activeIndex === index) {
        if (color) {
          style.color = color;
        }
      } else {
        if (inactiveColor) {
          style.color = inactiveColor;
        }
      }
      return common_vendor.objToStyle(style);
    };
    const getMapTabStyle = (index) => {
      const { color, inactiveColor } = props;
      const style = {};
      if (state.activeIndex === index) {
        if (color) {
          style.color = color;
          style.borderColor = color;
        }
      } else {
        if (inactiveColor) {
          style.color = inactiveColor;
        }
      }
      return common_vendor.objToStyle(style);
    };
    const updateActive = (value = 0, init = false, setScroll = true) => {
      if (children.length === 0)
        return;
      value = getActiveIndex(value);
      if (children[value].disabled)
        return;
      state.activeIndex = value;
      if (setScroll) {
        updateLineStyle(init === false);
        scrollIntoView();
      }
      setActiveTab();
    };
    const setActive = common_vendor.debounce(updateActive, 100, { leading: true });
    common_vendor.watch(
      () => props.modelValue,
      (newValue) => {
        const index = getActiveIndex(newValue);
        setActive(newValue, false, index !== state.activeIndex);
      },
      {
        immediate: false,
        deep: true
      }
    );
    common_vendor.watch(
      () => props.slidableNum,
      (newValue) => {
        common_vendor.checkNumRange(newValue, "slidableNum");
      }
    );
    common_vendor.watch(
      () => props.mapNum,
      (newValue) => {
        common_vendor.checkNumRange(newValue, "mapNum");
      }
    );
    common_vendor.onMounted(() => {
      state.inited = true;
      common_vendor.nextTick$1(() => {
        updateActive(props.modelValue, true);
        state.useInnerLine = true;
      });
    });
    function toggleMap() {
      if (state.mapShow) {
        state.animating = false;
        setTimeout(() => {
          state.mapShow = false;
        }, 300);
      } else {
        state.mapShow = true;
        setTimeout(() => {
          state.animating = true;
        }, 100);
      }
    }
    async function updateLineStyle(animation = true) {
      if (!state.inited)
        return;
      const { lineWidth, lineHeight, lineTheme } = props;
      try {
        const lineStyle = {};
        if (common_vendor.isDef(lineWidth)) {
          lineStyle.width = common_vendor.addUnit(lineWidth);
        } else {
          if (lineTheme === "text") {
            const textRects = await common_vendor.getRect($itemText, true, proxy);
            const textWidth = Number(textRects[state.activeIndex].width);
            lineStyle.width = common_vendor.addUnit(textWidth);
          } else if (lineTheme === "underline") {
            const rects2 = await common_vendor.getRect($item, true, proxy);
            const rectWidth = Number(rects2[state.activeIndex].width);
            lineStyle.width = common_vendor.addUnit(rectWidth);
          }
        }
        if (common_vendor.isDef(lineHeight)) {
          lineStyle.height = common_vendor.addUnit(lineHeight);
          lineStyle.borderRadius = `calc(${common_vendor.addUnit(lineHeight)} / 2)`;
        }
        const rects = await common_vendor.getRect($item, true, proxy);
        const rect = rects[state.activeIndex];
        let left = rects.slice(0, state.activeIndex).reduce((prev, curr) => prev + Number(curr.width), 0) + Number(rect.width) / 2;
        if (left) {
          lineStyle.transform = `translateX(${left}px) translateX(-50%)`;
          if (animation) {
            lineStyle.transition = "width 0.3s cubic-bezier(0.4, 0, 0.2, 1), transform 0.3s cubic-bezier(0.4, 0, 0.2, 1);";
          }
          state.useInnerLine = false;
          state.lineStyle = common_vendor.objToStyle(lineStyle);
        }
      } catch (error) {
        console.error("[wot ui] error(wd-tabs): update line style failed", error);
      }
    }
    function setActiveTab() {
      if (!state.inited)
        return;
      const name = getTabName(children[state.activeIndex], state.activeIndex);
      if (name !== props.modelValue) {
        emit("change", {
          index: state.activeIndex,
          name
        });
        emit("update:modelValue", name);
      }
    }
    function scrollIntoView() {
      if (!state.inited)
        return;
      Promise.all([common_vendor.getRect($item, true, proxy), common_vendor.getRect($container, false, proxy)]).then(([navItemsRects, navRect]) => {
        const selectItem = navItemsRects[state.activeIndex];
        const offsetLeft = navItemsRects.slice(0, state.activeIndex).reduce((prev, curr) => prev + curr.width, 0);
        const left = offsetLeft - (navRect.width - Number(selectItem.width)) / 2;
        if (left === state.scrollLeft) {
          state.scrollLeft = left + Math.random() / 1e4;
        } else {
          state.scrollLeft = left;
        }
      });
    }
    function handleSelect(index) {
      if (index === void 0)
        return;
      const { disabled } = children[index];
      const name = getTabName(children[index], index);
      if (disabled) {
        emit("disabled", {
          index,
          name
        });
        return;
      }
      state.mapShow && toggleMap();
      setActive(index);
      emit("click", {
        index,
        name
      });
    }
    function onTouchStart(event) {
      if (!props.swipeable)
        return;
      touch.touchStart(event);
    }
    function onTouchMove(event) {
      if (!props.swipeable)
        return;
      touch.touchMove(event);
    }
    function onTouchEnd() {
      if (!props.swipeable)
        return;
      const { direction, deltaX, offsetX } = touch;
      const minSwipeDistance = 50;
      if (direction.value === "horizontal" && offsetX.value >= minSwipeDistance) {
        if (deltaX.value > 0 && state.activeIndex !== 0) {
          setActive(state.activeIndex - 1);
        } else if (deltaX.value < 0 && state.activeIndex !== children.length - 1) {
          setActive(state.activeIndex + 1);
        }
      }
    }
    function getActiveIndex(value) {
      if (common_vendor.isNumber(value) && value >= children.length) {
        console.error("[wot ui] warning(wd-tabs): the type of tabs' value is Number shouldn't be less than its children");
        value = 0;
      }
      if (common_vendor.isString(value)) {
        const index = children.findIndex((item) => item.name === value);
        value = index === -1 ? 0 : index;
      }
      return value;
    }
    __expose({
      setActive,
      scrollIntoView,
      updateLineStyle
    });
    return (_ctx, _cache) => {
      return common_vendor.e({
        a: _ctx.sticky
      }, _ctx.sticky ? common_vendor.e({
        b: common_vendor.f(common_vendor.unref(children), (item, index, i0) => {
          return common_vendor.e({
            a: item.badgeProps
          }, item.badgeProps ? {
            b: common_vendor.t(item.title),
            c: "1c0b13ee-2-" + i0 + ",1c0b13ee-1",
            d: common_vendor.p({
              ...item.badgeProps
            })
          } : {
            e: common_vendor.t(item.title)
          }, {
            f: state.activeIndex === index && state.useInnerLine
          }, state.activeIndex === index && state.useInnerLine ? {
            g: common_vendor.n(`wd-tabs__line ${state.activeIndex === index && state.useInnerLine ? "is-" + _ctx.lineTheme : ""} wd-tabs__line--inner`)
          } : {}, {
            h: common_vendor.o(($event) => handleSelect(index), index),
            i: index,
            j: common_vendor.n(`wd-tabs__nav-item  ${state.activeIndex === index ? "is-active" : ""} ${item.disabled ? "is-disabled" : ""}`),
            k: common_vendor.s(getTabItemStyle(index))
          });
        }),
        c: common_vendor.n(`wd-tabs__line ${"is-" + _ctx.lineTheme}`),
        d: common_vendor.s(state.lineStyle),
        e: innerSlidable.value,
        f: state.scrollLeft,
        g: _ctx.mapNum < common_vendor.unref(children).length && _ctx.mapNum !== 0
      }, _ctx.mapNum < common_vendor.unref(children).length && _ctx.mapNum !== 0 ? {
        h: common_vendor.p({
          name: "down",
          ["custom-class"]: "wd-tabs__map-arrow-icon"
        }),
        i: common_vendor.n(`wd-tabs__map-arrow  ${state.animating ? "is-open" : ""}`),
        j: common_vendor.n(`wd-tabs__map-btn  ${state.animating ? "is-open" : ""}`),
        k: common_vendor.o(toggleMap),
        l: common_vendor.t(_ctx.mapTitle || common_vendor.unref(translate)("all")),
        m: common_vendor.s(mapHeaderStyle.value),
        n: common_vendor.f(common_vendor.unref(children), (item, index, i0) => {
          return {
            a: common_vendor.t(item.title),
            b: common_vendor.n(`wd-tabs__map-nav-btn ${state.activeIndex === index ? "is-active" : ""}  ${item.disabled ? "is-disabled" : ""}`),
            c: common_vendor.s(getMapTabStyle(index)),
            d: index,
            e: common_vendor.o(($event) => handleSelect(index), index)
          };
        }),
        o: common_vendor.n(`wd-tabs__map-body  ${state.animating ? "is-open" : ""}`),
        p: common_vendor.s(state.mapShow ? "" : "display:none")
      } : {}, {
        q: common_vendor.p({
          ["offset-top"]: _ctx.offsetTop
        }),
        r: common_vendor.n(_ctx.animated ? "is-animated" : ""),
        s: common_vendor.s(bodyStyle.value),
        t: common_vendor.o(onTouchStart),
        v: common_vendor.o(onTouchMove),
        w: common_vendor.o(onTouchEnd),
        x: common_vendor.o(onTouchEnd),
        y: common_vendor.s(mapHeaderStyle.value),
        z: common_vendor.o(toggleMap),
        A: common_vendor.n(`wd-tabs ${_ctx.customClass} ${innerSlidable.value ? "is-slide" : ""} ${_ctx.mapNum < common_vendor.unref(children).length && _ctx.mapNum !== 0 ? "is-map" : ""} ${!_ctx.showScrollbar ? "is-hide-scrollbar" : ""}`),
        B: common_vendor.s(_ctx.customStyle)
      }) : common_vendor.e({
        C: common_vendor.f(common_vendor.unref(children), (item, index, i0) => {
          return common_vendor.e({
            a: item.badgeProps
          }, item.badgeProps ? {
            b: common_vendor.t(item.title),
            c: "1c0b13ee-4-" + i0,
            d: common_vendor.p({
              ["custom-class"]: "wd-tabs__nav-item-badge",
              ...item.badgeProps
            })
          } : {
            e: common_vendor.t(item.title)
          }, {
            f: state.activeIndex === index && state.useInnerLine
          }, state.activeIndex === index && state.useInnerLine ? {
            g: common_vendor.n(`wd-tabs__line ${state.activeIndex === index && state.useInnerLine ? "is-" + _ctx.lineTheme : ""} wd-tabs__line--inner`)
          } : {}, {
            h: common_vendor.o(($event) => handleSelect(index), index),
            i: index,
            j: common_vendor.n(`wd-tabs__nav-item ${state.activeIndex === index ? "is-active" : ""} ${item.disabled ? "is-disabled" : ""}`),
            k: common_vendor.s(getTabItemStyle(index))
          });
        }),
        D: common_vendor.n(`wd-tabs__line ${"is-" + _ctx.lineTheme}`),
        E: common_vendor.s(state.lineStyle),
        F: innerSlidable.value,
        G: state.scrollLeft,
        H: _ctx.mapNum < common_vendor.unref(children).length && _ctx.mapNum !== 0
      }, _ctx.mapNum < common_vendor.unref(children).length && _ctx.mapNum !== 0 ? {
        I: common_vendor.p({
          name: "down",
          ["custom-class"]: "wd-tabs__map-arrow-icon"
        }),
        J: common_vendor.n(`wd-tabs__map-arrow ${state.animating ? "is-open" : ""}`),
        K: common_vendor.o(toggleMap),
        L: common_vendor.t(_ctx.mapTitle || common_vendor.unref(translate)("all")),
        M: common_vendor.s(mapHeaderStyle.value),
        N: common_vendor.f(common_vendor.unref(children), (item, index, i0) => {
          return {
            a: common_vendor.t(item.title),
            b: common_vendor.n(`wd-tabs__map-nav-btn ${state.activeIndex === index ? "is-active" : ""}  ${item.disabled ? "is-disabled" : ""}`),
            c: index,
            d: common_vendor.o(($event) => handleSelect(index), index)
          };
        }),
        O: common_vendor.n(`wd-tabs__map-body ${state.animating ? "is-open" : ""}`),
        P: common_vendor.s(state.mapShow ? "" : "display:none")
      } : {}, {
        Q: common_vendor.n(_ctx.animated ? "is-animated" : ""),
        R: common_vendor.s(bodyStyle.value),
        S: common_vendor.o(onTouchStart),
        T: common_vendor.o(onTouchMove),
        U: common_vendor.o(onTouchEnd),
        V: common_vendor.o(onTouchEnd),
        W: common_vendor.s(mapHeaderStyle.value),
        X: common_vendor.o(toggleMap),
        Y: common_vendor.n(`wd-tabs ${_ctx.customClass} ${innerSlidable.value ? "is-slide" : ""} ${_ctx.mapNum < common_vendor.unref(children).length && _ctx.mapNum !== 0 ? "is-map" : ""} ${!_ctx.showScrollbar ? "is-hide-scrollbar" : ""}`)
      }));
    };
  }
});
exports._sfc_main = _sfc_main;
