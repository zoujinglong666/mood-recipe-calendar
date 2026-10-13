"use strict";
const common_vendor = require("../../../../../common/vendor.js");
const __default__ = {
  name: "wd-root-portal",
  options: {
    virtualHost: true,
    addGlobalClass: true,
    styleIsolation: "shared"
  }
};
const _sfc_main = /* @__PURE__ */ common_vendor.defineComponent({
  ...__default__,
  props: {
    ...common_vendor.baseProps
  },
  setup(__props) {
    const None = Symbol("None");
    const hooksProvider = common_vendor.inject(common_vendor.USE_CONFIG_PROVIDER_KEY, None);
    const { parent: configProvider } = common_vendor.useParent(common_vendor.CONFIG_PROVIDER_KEY);
    const configProviderStyle = common_vendor.computed(() => {
      var _a;
      return hooksProvider !== None ? hooksProvider.themeStyle.value || "" : ((_a = configProvider.value) == null ? void 0 : _a.themeStyle.value) || "";
    });
    const themeClass = common_vendor.computed(() => {
      var _a, _b, _c;
      const theme = hooksProvider !== None ? (_a = hooksProvider.globalConfig) == null ? void 0 : _a.value.theme : (_c = (_b = configProvider.value) == null ? void 0 : _b.globalConfig) == null ? void 0 : _c.value.theme;
      return theme ? `wot-theme-${theme}` : "wot-theme-light";
    });
    return (_ctx, _cache) => {
      return {
        a: common_vendor.n(themeClass.value),
        b: common_vendor.s(configProviderStyle.value)
      };
    };
  }
});
exports._sfc_main = _sfc_main;
