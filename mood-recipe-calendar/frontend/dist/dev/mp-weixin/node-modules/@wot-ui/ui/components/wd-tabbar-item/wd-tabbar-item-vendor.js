"use strict";
const common_vendor = require("../../../../../common/vendor.js");
if (!Math) {
  (wdIcon + wdBadge)();
}
const wdBadge = () => "../wd-badge/wd-badge.js";
const wdIcon = () => "../wd-icon/wd-icon.js";
const __default__ = {
  name: "wd-tabbar-item",
  options: {
    addGlobalClass: true,
    virtualHost: true,
    styleIsolation: "shared"
  }
};
const _sfc_main = /* @__PURE__ */ common_vendor.defineComponent({
  ...__default__,
  props: common_vendor.tabbarItemProps,
  setup(__props) {
    const props = __props;
    const { parent: tabbar, index } = common_vendor.useParent(common_vendor.TABBAR_KEY);
    const hasIcon = common_vendor.computed(() => Boolean(props.icon || common_vendor.isString(props.cssIcon) && props.cssIcon));
    const customBadgeProps = common_vendor.computed(() => {
      const badgeProps = common_vendor.deepAssign(
        common_vendor.isDef(props.badgeProps) ? common_vendor.omitBy(props.badgeProps, common_vendor.isUndefined) : {},
        common_vendor.omitBy(
          {
            max: props.max,
            isDot: props.isDot,
            value: props.value
          },
          common_vendor.isUndefined
        )
      );
      if (!common_vendor.isDef(badgeProps.max)) {
        badgeProps.max = 99;
      }
      return badgeProps;
    });
    const textStyle = common_vendor.computed(() => {
      const style = {};
      if (tabbar.value) {
        if (active.value && tabbar.value.props.activeColor) {
          style["color"] = tabbar.value.props.activeColor;
        }
        if (!active.value && tabbar.value.props.inactiveColor) {
          style["color"] = tabbar.value.props.inactiveColor;
        }
      }
      return `${common_vendor.objToStyle(style)}`;
    });
    const active = common_vendor.computed(() => {
      const name = common_vendor.isDef(props.name) ? props.name : index.value;
      if (tabbar.value) {
        if (tabbar.value.props.modelValue === name) {
          return true;
        } else {
          return false;
        }
      } else {
        return false;
      }
    });
    function handleClick() {
      const name = common_vendor.isDef(props.name) ? props.name : index.value;
      tabbar.value && tabbar.value.setChange({ name });
    }
    return (_ctx, _cache) => {
      return common_vendor.e({
        a: common_vendor.r("icon", {
          active: active.value
        }),
        b: !_ctx.$slots.icon && hasIcon.value
      }, !_ctx.$slots.icon && hasIcon.value ? {
        c: common_vendor.p({
          name: _ctx.icon,
          ["class-prefix"]: _ctx.iconPrefix,
          ["css-icon"]: _ctx.cssIcon,
          ["custom-style"]: textStyle.value,
          ["custom-class"]: `wd-tabbar-item__body-icon ${active.value ? "is-active" : "is-inactive"}`
        })
      } : {}, {
        d: _ctx.title
      }, _ctx.title ? {
        e: common_vendor.t(_ctx.title),
        f: common_vendor.s(textStyle.value),
        g: common_vendor.n(`wd-tabbar-item__body-title ${active.value ? "is-active" : "is-inactive"}`)
      } : {}, {
        h: common_vendor.p({
          ...customBadgeProps.value
        }),
        i: common_vendor.n(`wd-tabbar-item ${_ctx.customClass}`),
        j: common_vendor.s(_ctx.customStyle),
        k: common_vendor.o(handleClick)
      });
    };
  }
});
exports._sfc_main = _sfc_main;
