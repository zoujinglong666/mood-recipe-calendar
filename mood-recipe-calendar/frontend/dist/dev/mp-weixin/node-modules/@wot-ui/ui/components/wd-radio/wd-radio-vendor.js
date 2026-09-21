"use strict";
const common_vendor = require("../../../../../common/vendor.js");
if (!Math) {
  wdIcon();
}
const wdIcon = () => "../wd-icon/wd-icon.js";
const __default__ = {
  name: "wd-radio",
  options: {
    virtualHost: true,
    addGlobalClass: true,
    styleIsolation: "shared"
  }
};
const _sfc_main = /* @__PURE__ */ common_vendor.defineComponent({
  ...__default__,
  props: common_vendor.radioProps,
  setup(__props) {
    const props = __props;
    const { parent: radioGroup } = common_vendor.useParent(common_vendor.RADIO_GROUP_KEY);
    const formDisabled = common_vendor.useFormDisabled(props);
    const isChecked = common_vendor.computed(() => {
      if (radioGroup.value) {
        return props.value === radioGroup.value.props.modelValue;
      } else {
        return false;
      }
    });
    const typeValue = common_vendor.computed(() => {
      return props.type || common_vendor.getPropByPath(radioGroup.value, "props.type");
    });
    const iconValue = common_vendor.computed(() => {
      let icon = "";
      switch (typeValue.value) {
        case "circle":
          icon = isChecked.value ? "check-circle-fill" : "uncheck-circle";
          break;
        case "square":
          icon = isChecked.value ? "check-square-fill" : "uncheck-square";
          break;
        case "dot":
          icon = isChecked.value ? "check-circle-radio-fill" : "uncheck-circle";
          break;
        case "button":
          icon = isChecked.value ? "check" : "";
          break;
      }
      return icon;
    });
    const checkedColorValue = common_vendor.computed(() => {
      return props.checkedColor || common_vendor.getPropByPath(radioGroup.value, "props.checkedColor");
    });
    const uncheckedColorValue = common_vendor.computed(() => {
      return props.uncheckedColor || common_vendor.getPropByPath(radioGroup.value, "props.uncheckedColor");
    });
    const readonlyValue = common_vendor.computed(() => {
      if (common_vendor.isDef(props.readonly)) {
        return props.readonly;
      } else {
        return common_vendor.getPropByPath(radioGroup.value, "props.readonly");
      }
    });
    const iconStyle = common_vendor.computed(() => {
      if (isButton.value)
        return "";
      if (isChecked.value && checkedColorValue.value) {
        return `color: ${checkedColorValue.value}`;
      }
      if (!isChecked.value && uncheckedColorValue.value) {
        return `color: ${uncheckedColorValue.value}`;
      }
      return "";
    });
    const isButton = common_vendor.computed(() => {
      return typeValue.value === "button";
    });
    const disabledValue = common_vendor.computed(() => {
      if (formDisabled.value) {
        return true;
      }
      if (common_vendor.isDef(props.disabled)) {
        return props.disabled;
      } else {
        return common_vendor.getPropByPath(radioGroup.value, "props.disabled");
      }
    });
    const directionValue = common_vendor.computed(() => {
      if (common_vendor.isDef(props.direction)) {
        return props.direction;
      } else {
        return common_vendor.getPropByPath(radioGroup.value, "props.direction");
      }
    });
    const placementValue = common_vendor.computed(() => {
      if (common_vendor.isDef(props.placement)) {
        return props.placement;
      } else {
        return common_vendor.getPropByPath(radioGroup.value, "props.placement");
      }
    });
    common_vendor.watch(
      () => props.type,
      (newValue) => {
        const type = ["check", "dot", "button", "square"];
        if (!newValue || type.indexOf(newValue) === -1)
          console.error(`type must be one of ${type.toString()}`);
      }
    );
    function handleClick() {
      const { value } = props;
      if (!disabledValue.value && !readonlyValue.value && radioGroup.value && common_vendor.isDef(value)) {
        const allowUncheck = radioGroup.value.props.allowUncheck;
        if (allowUncheck && isChecked.value) {
          radioGroup.value.updateValue(null);
        } else {
          radioGroup.value.updateValue(value);
        }
      }
    }
    return (_ctx, _cache) => {
      return common_vendor.e({
        a: _ctx.$slots.default
      }, _ctx.$slots.default ? {
        b: common_vendor.n(`wd-radio__label ${_ctx.customLabelClass || ""}`)
      } : {}, {
        c: isButton.value
      }, isButton.value ? common_vendor.e({
        d: isChecked.value
      }, isChecked.value ? {
        e: common_vendor.p({
          ["custom-class"]: "wd-radio__icon",
          ["custom-style"]: iconStyle.value,
          name: iconValue.value
        })
      } : {}, {
        f: common_vendor.r("icon", {
          isChecked: isChecked.value
        })
      }) : {
        g: common_vendor.p({
          ["custom-class"]: "wd-radio__icon",
          ["custom-style"]: iconStyle.value,
          name: iconValue.value
        }),
        h: common_vendor.r("icon", {
          isChecked: isChecked.value
        })
      }, {
        i: common_vendor.n(`wd-radio wd-radio--${placementValue.value}  ${isButton.value ? "is-button" : "wd-radio--" + directionValue.value} ${isChecked.value ? "is-checked" : ""} ${disabledValue.value ? "is-disabled" : ""} ${_ctx.customClass}`),
        j: common_vendor.s(_ctx.customStyle),
        k: common_vendor.o(handleClick)
      });
    };
  }
});
exports._sfc_main = _sfc_main;
