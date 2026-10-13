"use strict";
const common_vendor = require("../common/vendor.js");
const composables_useManualTheme = require("../composables/useManualTheme.js");
const __unplugin_components_0 = () => "../node-modules/@wot-ui/ui/components/wd-config-provider/wd-config-provider.js";
if (!Array) {
  const _component_wd_config_provider = __unplugin_components_0;
  _component_wd_config_provider();
}
const __default__ = {
  options: {
    addGlobalClass: true,
    virtualHost: true,
    styleIsolation: "shared"
  }
};
const _sfc_main = /* @__PURE__ */ common_vendor.defineComponent({
  ...__default__,
  __name: "default",
  setup(__props) {
    const { theme, themeVars } = composables_useManualTheme.useManualTheme();
    return (_ctx, _cache) => {
      return {
        a: common_vendor.unref(theme) === "dark" ? 1 : "",
        b: common_vendor.p({
          theme: common_vendor.unref(theme),
          ["theme-vars"]: common_vendor.unref(themeVars)
        })
      };
    };
  }
});
exports._sfc_main = _sfc_main;
