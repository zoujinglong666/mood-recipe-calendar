"use strict";
const common_vendor = require("../../../../../common/vendor.js");
if (!Math) {
  wdIcon();
}
const wdIcon = () => "../wd-icon/wd-icon.js";
const __default__ = {
  name: "wd-collapse",
  options: {
    addGlobalClass: true,
    virtualHost: true,
    styleIsolation: "shared"
  }
};
const _sfc_main = /* @__PURE__ */ common_vendor.defineComponent({
  ...__default__,
  props: common_vendor.collapseProps,
  emits: ["change", "update:modelValue"],
  setup(__props, { expose: __expose, emit: __emit }) {
    const props = __props;
    const emit = __emit;
    const { translate } = common_vendor.useTranslate("collapse");
    const innerValue = common_vendor.ref(getDefaultValue());
    const currentValue = common_vendor.computed(() => common_vendor.isDef(props.modelValue) ? props.modelValue : innerValue.value);
    const contentLineNum = common_vendor.computed(() => props.viewmore && !currentValue.value ? props.lineNum : 0);
    const { linkChildren, children } = common_vendor.useChildren(common_vendor.COLLAPSE_KEY);
    linkChildren({ props, currentValue, toggle });
    function getDefaultValue() {
      if (props.viewmore) {
        return false;
      }
      return props.accordion ? "" : [];
    }
    common_vendor.watch(
      () => props.modelValue,
      (newVal) => {
        const { viewmore, accordion } = props;
        if (!common_vendor.isDef(newVal)) {
          return;
        }
        if (accordion && typeof newVal !== "string") {
          console.error("accordion value must be string");
        } else if (!accordion && !viewmore && !common_vendor.isArray(newVal)) {
          console.error("value must be Array");
        }
      },
      { deep: true }
    );
    common_vendor.watch(
      () => [props.accordion, props.viewmore],
      () => {
        if (!common_vendor.isDef(props.modelValue)) {
          innerValue.value = getDefaultValue();
        }
      }
    );
    common_vendor.watch(
      () => props.lineNum,
      (newVal) => {
        if (newVal <= 0) {
          console.error("lineNum must greater than 0");
        }
      },
      { deep: true, immediate: true }
    );
    function updateChange(activeNames) {
      if (!common_vendor.isDef(props.modelValue)) {
        innerValue.value = activeNames;
      }
      emit("update:modelValue", activeNames);
      emit("change", {
        value: activeNames
      });
    }
    function toggle(name, expanded) {
      const { accordion } = props;
      const modelValue = currentValue.value;
      if (accordion) {
        updateChange(name === modelValue ? "" : name);
      } else if (expanded) {
        const activeNames = common_vendor.isArray(modelValue) ? modelValue : [];
        updateChange(activeNames.concat(name));
      } else {
        const activeNames = common_vendor.isArray(modelValue) ? modelValue : [];
        updateChange(activeNames.filter((activeName) => activeName !== name));
      }
    }
    const toggleAll = (options = {}) => {
      if (props.accordion) {
        return;
      }
      if (common_vendor.isBoolean(options)) {
        options = { expanded: options };
      }
      const { expanded, skipDisabled } = options;
      const names = [];
      children.forEach((item, index) => {
        if (item.disabled && skipDisabled) {
          if (item.$.exposed.getExpanded()) {
            names.push(item.name || index);
          }
        } else if (common_vendor.isDef(expanded) ? expanded : !item.$.exposed.getExpanded()) {
          names.push(item.name || index);
        }
      });
      updateChange(names);
    };
    function handleMore() {
      updateChange(!currentValue.value);
    }
    __expose({
      toggleAll
    });
    return (_ctx, _cache) => {
      return common_vendor.e({
        a: !_ctx.viewmore
      }, !_ctx.viewmore ? {} : common_vendor.e({
        b: common_vendor.n(`wd-collapse__content ${!currentValue.value ? "is-retract" : ""} `),
        c: common_vendor.s(`-webkit-line-clamp: ${contentLineNum.value}; -webkit-box-orient: vertical`),
        d: _ctx.useMoreSlot
      }, _ctx.useMoreSlot ? {
        e: common_vendor.n(_ctx.customMoreSlotClass)
      } : {
        f: common_vendor.t(!currentValue.value ? common_vendor.unref(translate)("expand") : common_vendor.unref(translate)("retract")),
        g: common_vendor.p({
          name: "down"
        }),
        h: common_vendor.n(`wd-collapse__arrow ${currentValue.value ? "is-retract" : ""}`)
      }, {
        i: common_vendor.o(handleMore)
      }), {
        j: common_vendor.n(`wd-collapse ${_ctx.viewmore ? "is-viewmore" : ""} ${_ctx.customClass}`),
        k: common_vendor.s(_ctx.customStyle)
      });
    };
  }
});
exports._sfc_main = _sfc_main;
