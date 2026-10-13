"use strict";
const common_vendor = require("../../../../../common/vendor.js");
if (!Math) {
  wdIcon();
}
const wdIcon = () => "../wd-icon/wd-icon.js";
const __default__ = {
  name: "wd-navbar",
  options: {
    virtualHost: true,
    addGlobalClass: true,
    styleIsolation: "shared"
  }
};
const _sfc_main = /* @__PURE__ */ common_vendor.defineComponent({
  ...__default__,
  props: common_vendor.navbarProps,
  emits: ["click-left", "click-right"],
  setup(__props, { emit: __emit }) {
    const props = __props;
    const emit = __emit;
    const height = common_vendor.ref("");
    const { statusBarHeight, navBarHeight } = common_vendor.useDeviceInfo();
    common_vendor.watch(
      [() => props.fixed, () => props.placeholder],
      () => {
        setPlaceholderHeight();
      },
      { deep: true, immediate: false }
    );
    const rootStyle = common_vendor.computed(() => {
      const style = {};
      if (props.fixed && common_vendor.isDef(props.zIndex)) {
        style["z-index"] = props.zIndex;
      }
      if (props.safeAreaInsetTop) {
        style["padding-top"] = common_vendor.addUnit(statusBarHeight.value || 0);
      }
      if (navBarHeight.value) {
        style["height"] = common_vendor.addUnit(navBarHeight.value);
        style["line-height"] = common_vendor.addUnit(navBarHeight.value);
      }
      return `${common_vendor.objToStyle(style)}${props.customStyle}`;
    });
    common_vendor.onMounted(() => {
      if (props.fixed && props.placeholder) {
        common_vendor.nextTick$1(() => {
          setPlaceholderHeight();
        });
      }
    });
    function handleClickLeft() {
      if (!props.leftDisabled) {
        emit("click-left");
      }
    }
    function handleClickRight() {
      if (!props.rightDisabled) {
        emit("click-right");
      }
    }
    const { proxy } = common_vendor.getCurrentInstance();
    function setPlaceholderHeight() {
      if (!props.fixed || !props.placeholder) {
        return;
      }
      common_vendor.getRect(".wd-navbar", false, proxy).then((res) => {
        height.value = res.height;
      });
    }
    return (_ctx, _cache) => {
      return common_vendor.e({
        a: _ctx.$slots.capsule
      }, _ctx.$slots.capsule ? {} : !_ctx.$slots.left ? common_vendor.e({
        c: _ctx.leftArrow
      }, _ctx.leftArrow ? {
        d: common_vendor.p({
          name: "left",
          ["custom-class"]: "wd-navbar__arrow"
        })
      } : {}, {
        e: _ctx.leftText
      }, _ctx.leftText ? {
        f: common_vendor.t(_ctx.leftText)
      } : {}, {
        g: common_vendor.n(`wd-navbar__left ${_ctx.leftDisabled ? "is-disabled" : ""}`),
        h: common_vendor.o(handleClickLeft)
      }) : {
        i: common_vendor.n(`wd-navbar__left ${_ctx.leftDisabled ? "is-disabled" : ""}`),
        j: common_vendor.o(handleClickLeft)
      }, {
        b: !_ctx.$slots.left,
        k: !_ctx.$slots.title && _ctx.title
      }, !_ctx.$slots.title && _ctx.title ? {
        l: common_vendor.t(_ctx.title)
      } : {}, {
        m: _ctx.$slots.right || _ctx.rightText
      }, _ctx.$slots.right || _ctx.rightText ? common_vendor.e({
        n: !_ctx.$slots.right && _ctx.rightText
      }, !_ctx.$slots.right && _ctx.rightText ? {
        o: common_vendor.t(_ctx.rightText)
      } : {}, {
        p: common_vendor.n(`wd-navbar__right ${_ctx.rightDisabled ? "is-disabled" : ""}`),
        q: common_vendor.o(handleClickRight)
      }) : {}, {
        r: common_vendor.n(`wd-navbar ${_ctx.customClass} ${_ctx.fixed ? "is-fixed" : ""} ${_ctx.bordered ? "is-border" : ""}`),
        s: common_vendor.s(rootStyle.value),
        t: common_vendor.unref(common_vendor.addUnit)(height.value)
      });
    };
  }
});
exports._sfc_main = _sfc_main;
