"use strict";
const common_vendor = require("../../../../../common/vendor.js");
if (!Math) {
  (wdIcon + wdLoading)();
}
const wdIcon = () => "../wd-icon/wd-icon.js";
const wdLoading = () => "../wd-loading/wd-loading.js";
const __default__ = {
  name: "wd-switch",
  options: {
    addGlobalClass: true,
    virtualHost: true,
    styleIsolation: "shared"
  }
};
const _sfc_main = /* @__PURE__ */ common_vendor.defineComponent({
  ...__default__,
  props: common_vendor.switchProps,
  emits: ["change", "update:modelValue"],
  setup(__props, { emit: __emit }) {
    const props = __props;
    const emit = __emit;
    const isDisabled = common_vendor.useFormDisabled(props);
    const isActive = common_vendor.computed(() => {
      return props.modelValue === props.activeValue;
    });
    const rootClass = common_vendor.computed(() => {
      return `wd-switch ${props.customClass} ${isDisabled.value ? "is-disabled" : ""} ${props.loading ? "is-loading" : ""} wd-switch--${props.shape} is-${isActive.value ? "active" : "inactive"}`;
    });
    const rootStyle = common_vendor.computed(() => {
      const style = {};
      if (props.size) {
        style["font-size"] = common_vendor.addUnit(props.size);
      }
      return `${common_vendor.objToStyle(style)}${props.customStyle}`;
    });
    const innerStyle = common_vendor.computed(() => {
      const style = {
        background: isActive.value ? props.activeColor : props.inactiveColor
      };
      return `${common_vendor.objToStyle(style)}$`;
    });
    const innerIcon = common_vendor.computed(() => {
      return isActive.value ? props.activeIcon : props.inactiveIcon;
    });
    const innerCssIcon = common_vendor.computed(() => {
      return isActive.value ? props.activeCssIcon : props.inactiveCssIcon;
    });
    const actionIcon = common_vendor.computed(() => {
      return isActive.value ? props.activeActionIcon : props.inactiveActionIcon;
    });
    const actionCssIcon = common_vendor.computed(() => {
      return isActive.value ? props.activeActionCssIcon : props.inactiveActionCssIcon;
    });
    const customLoadingProps = common_vendor.computed(() => {
      const overrides = common_vendor.isDef(props.loadingProps) ? common_vendor.omitBy(props.loadingProps, common_vendor.isUndefined) : {};
      const customSpinnerClass = [common_vendor.isDef(overrides.customSpinnerClass) ? overrides.customSpinnerClass : "", "wd-switch__action-loading"].filter(Boolean).join(" ");
      const type = common_vendor.isDef(overrides.type) ? overrides.type : "spinner";
      return { ...overrides, customSpinnerClass, type };
    });
    function switchValue() {
      if (isDisabled.value || props.loading)
        return;
      const newVal = isActive.value ? props.inactiveValue : props.activeValue;
      const doSwitch = () => {
        emit("update:modelValue", newVal);
        emit("change", {
          value: newVal
        });
      };
      common_vendor.callInterceptor(props.beforeChange, {
        args: [newVal],
        done: doSwitch
      });
    }
    common_vendor.onBeforeMount(() => {
      if ([props.activeValue, props.inactiveValue].indexOf(props.modelValue) === -1) {
        emit("update:modelValue", props.inactiveValue);
        emit("change", {
          value: props.inactiveValue
        });
      }
    });
    return (_ctx, _cache) => {
      return common_vendor.e({
        a: innerIcon.value || innerCssIcon.value
      }, innerIcon.value || innerCssIcon.value ? {
        b: common_vendor.p({
          ["custom-class"]: "wd-switch__inner-icon",
          name: innerIcon.value,
          ["class-prefix"]: _ctx.classPrefix,
          ["css-icon"]: innerCssIcon.value
        })
      } : {
        c: common_vendor.t(isActive.value ? _ctx.activeText : _ctx.inactiveText)
      }, {
        d: common_vendor.s(innerStyle.value),
        e: _ctx.loading
      }, _ctx.loading ? {
        f: common_vendor.p({
          ...customLoadingProps.value
        })
      } : actionIcon.value || actionCssIcon.value ? {
        h: common_vendor.p({
          ["custom-class"]: "wd-switch__action-icon",
          name: actionIcon.value,
          ["class-prefix"]: _ctx.classPrefix,
          ["css-icon"]: actionCssIcon.value
        })
      } : {}, {
        g: actionIcon.value || actionCssIcon.value,
        i: common_vendor.n(rootClass.value),
        j: common_vendor.s(rootStyle.value),
        k: common_vendor.o(switchValue)
      });
    };
  }
});
exports._sfc_main = _sfc_main;
