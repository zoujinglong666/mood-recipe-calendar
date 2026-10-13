"use strict";
const common_vendor = require("../../../../../common/vendor.js");
const __default__ = {
  name: "wd-config-provider",
  options: {
    virtualHost: true,
    addGlobalClass: true,
    styleIsolation: "shared"
  }
};
const _sfc_main = /* @__PURE__ */ common_vendor.defineComponent({
  ...__default__,
  props: common_vendor.configProviderProps,
  setup(__props) {
    const None = Symbol("None");
    const hooksProvider = common_vendor.inject(common_vendor.USE_CONFIG_PROVIDER_KEY, None);
    const props = __props;
    const { linkChildren } = common_vendor.useChildren(common_vendor.CONFIG_PROVIDER_KEY);
    const { parent: parentConfigProvider } = common_vendor.useParent(common_vendor.CONFIG_PROVIDER_KEY);
    const themeClass = common_vendor.computed(() => {
      return `wot-theme-${props.theme} ${props.customClass}`;
    });
    const themeStyle = common_vendor.computed(() => {
      if (hooksProvider !== None) {
        return hooksProvider.themeStyle.value;
      }
      const styleObj = common_vendor.mapThemeVarsToCSSVars(props.themeVars);
      return styleObj ? `${common_vendor.objToStyle(styleObj)}` : "";
    });
    const rootStyle = common_vendor.computed(() => {
      const style = `${themeStyle.value}${props.customStyle}`;
      return style;
    });
    const globalConfig = common_vendor.computed(() => {
      var _a;
      const parentGlobalConfig = ((_a = parentConfigProvider.value) == null ? void 0 : _a.globalConfig.value) || null;
      return common_vendor.mergeConfig(parentGlobalConfig, {
        theme: props.theme,
        themeVars: props.themeVars,
        button: props.button,
        tag: props.tag
      });
    });
    linkChildren({
      themeStyle,
      globalConfig
    });
    return (_ctx, _cache) => {
      return {
        a: _ctx.theme,
        b: common_vendor.n(themeClass.value),
        c: common_vendor.s(rootStyle.value)
      };
    };
  }
});
exports._sfc_main = _sfc_main;
