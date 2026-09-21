"use strict";
const common_vendor = require("../../../../../common/vendor.js");
const __default__ = {
  name: "wd-tabbar",
  options: {
    addGlobalClass: true,
    virtualHost: true,
    styleIsolation: "shared"
  }
};
const _sfc_main = /* @__PURE__ */ common_vendor.defineComponent({
  ...__default__,
  props: common_vendor.tabbarProps,
  emits: ["change", "update:modelValue"],
  setup(__props, { emit: __emit }) {
    const props = __props;
    const emit = __emit;
    const height = common_vendor.ref("");
    const { proxy } = common_vendor.getCurrentInstance();
    const { linkChildren } = common_vendor.useChildren(common_vendor.TABBAR_KEY);
    linkChildren({
      props,
      setChange
    });
    const rootStyle = common_vendor.computed(() => {
      const style = {};
      if (common_vendor.isDef(props.zIndex)) {
        style["z-index"] = props.zIndex;
      }
      return `${common_vendor.objToStyle(style)}${props.customStyle}`;
    });
    common_vendor.watch(
      [() => props.fixed, () => props.placeholder],
      () => {
        setPlaceholderHeight();
      },
      { deep: true, immediate: false }
    );
    common_vendor.onMounted(() => {
      if (props.fixed && props.placeholder) {
        common_vendor.nextTick$1(() => {
          setPlaceholderHeight();
        });
      }
    });
    function setChange(child) {
      let active = child.name;
      if (active === props.modelValue) {
        return;
      }
      const change = () => {
        emit("update:modelValue", active);
        emit("change", {
          value: active
        });
      };
      common_vendor.callInterceptor(props.beforeChange, { args: [active], done: change });
    }
    function setPlaceholderHeight() {
      if (!props.fixed || !props.placeholder) {
        return;
      }
      common_vendor.getRect(".wd-tabbar", false, proxy).then((res) => {
        height.value = Number(res.height);
      });
    }
    return (_ctx, _cache) => {
      return {
        a: common_vendor.n(`wd-tabbar wd-tabbar--${_ctx.shape} ${_ctx.customClass} ${_ctx.fixed ? "is-fixed" : ""} ${_ctx.safeAreaInsetBottom ? "is-safe" : ""} ${_ctx.bordered ? "is-border" : ""}`),
        b: common_vendor.s(rootStyle.value),
        c: _ctx.fixed && _ctx.placeholder && _ctx.safeAreaInsetBottom && _ctx.shape === "round" ? 1 : "",
        d: common_vendor.unref(common_vendor.addUnit)(height.value)
      };
    };
  }
});
exports._sfc_main = _sfc_main;
