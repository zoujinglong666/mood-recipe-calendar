"use strict";
const common_vendor = require("../../../../../common/vendor.js");
if (!Math) {
  wdIcon();
}
const wdIcon = () => "../wd-icon/wd-icon.js";
const __default__ = {
  name: "wd-collapse-item",
  options: {
    addGlobalClass: true,
    virtualHost: true,
    styleIsolation: "shared"
  }
};
const _sfc_main = /* @__PURE__ */ common_vendor.defineComponent({
  ...__default__,
  props: common_vendor.collapseItemProps,
  setup(__props, { expose: __expose }) {
    const collapseId = common_vendor.ref(`collapseId${common_vendor.uuid()}`);
    const props = __props;
    const { parent: collapse, index } = common_vendor.useParent(common_vendor.COLLAPSE_KEY);
    const height = common_vendor.ref("");
    const inited = common_vendor.ref(false);
    const expanded = common_vendor.ref(false);
    const { proxy } = common_vendor.getCurrentInstance();
    const isFirst = common_vendor.computed(() => {
      return index.value === 0;
    });
    const contentStyle = common_vendor.computed(() => {
      const style = {};
      if (inited.value) {
        style.transition = "height 0.3s ease-in-out";
      }
      if (!expanded.value) {
        style.height = "0px";
      } else if (height.value) {
        style.height = common_vendor.addUnit(height.value);
      }
      return common_vendor.objToStyle(style);
    });
    const isSelected = common_vendor.computed(() => {
      const modelValue = collapse.value && collapse.value.currentValue ? collapse.value.currentValue.value : [];
      const { name } = props;
      return common_vendor.isString(modelValue) && modelValue === name || common_vendor.isArray(modelValue) && modelValue.indexOf(name) >= 0;
    });
    common_vendor.watch(
      () => isSelected.value,
      (newVal) => {
        updateExpand(newVal);
      }
    );
    common_vendor.onMounted(() => {
      updateExpand(isSelected.value);
    });
    async function updateExpand(useBeforeExpand = true) {
      try {
        if (useBeforeExpand) {
          await handleBeforeExpand();
        }
        initRect();
      } catch (error) {
      }
    }
    function initRect() {
      common_vendor.getRect(`#${collapseId.value}`, false, proxy).then(async (rect) => {
        const { height: rectHeight } = rect;
        height.value = common_vendor.isDef(rectHeight) ? Number(rectHeight) : "";
        await common_vendor.pause();
        if (isSelected.value) {
          expanded.value = true;
        } else {
          expanded.value = false;
        }
        if (!inited.value) {
          inited.value = true;
        }
      });
    }
    function handleTransitionEnd() {
      if (expanded.value) {
        height.value = "";
      }
    }
    async function handleClick() {
      if (props.disabled)
        return;
      try {
        await updateExpand();
        const { name } = props;
        collapse.value && collapse.value.toggle(name, !expanded.value);
      } catch (error) {
      }
    }
    function handleBeforeExpand() {
      return new Promise((resolve, reject) => {
        const { name } = props;
        const nextexpanded = !expanded.value;
        if (nextexpanded) {
          common_vendor.callInterceptor(props.beforeExpend, {
            args: [name],
            done: resolve,
            canceled: reject,
            error: reject
          });
        } else {
          resolve();
        }
      });
    }
    function getExpanded() {
      return expanded.value;
    }
    __expose({ getExpanded, updateExpand });
    return (_ctx, _cache) => {
      return {
        a: common_vendor.t(_ctx.title),
        b: common_vendor.p({
          name: "down",
          ["custom-class"]: `wd-collapse-item__arrow ${expanded.value ? "is-retract" : ""}`
        }),
        c: common_vendor.r("title", {
          expanded: expanded.value,
          disabled: _ctx.disabled,
          isFirst: isFirst.value
        }),
        d: common_vendor.n(`wd-collapse-item__header ${expanded.value ? "is-expanded" : ""} ${isFirst.value ? "wd-collapse-item__header-first" : ""} ${_ctx.$slots.title ? "is-custom" : ""}`),
        e: common_vendor.o(handleClick),
        f: common_vendor.n(_ctx.customBodyClass),
        g: common_vendor.s(_ctx.customBodyStyle),
        h: collapseId.value,
        i: common_vendor.s(contentStyle.value),
        j: common_vendor.o(handleTransitionEnd),
        k: common_vendor.n(`wd-collapse-item ${_ctx.disabled ? "is-disabled" : ""} ${_ctx.border ? "is-border" : ""} ${_ctx.customClass}`),
        l: common_vendor.s(_ctx.customStyle)
      };
    };
  }
});
exports._sfc_main = _sfc_main;
