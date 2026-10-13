"use strict";
const common_vendor = require("../common/vendor.js");
const composables_useManualTheme = require("../composables/useManualTheme.js");
const composables_useTabbar = require("../composables/useTabbar.js");
const __unplugin_components_3 = () => "../node-modules/@wot-ui/ui/components/wd-config-provider/wd-config-provider.js";
const __unplugin_components_2 = () => "../node-modules/@wot-ui/ui/components/wd-tabbar/wd-tabbar.js";
const __unplugin_components_1 = () => "../node-modules/@wot-ui/ui/components/wd-tabbar-item/wd-tabbar-item.js";
const __unplugin_components_0 = () => "../node-modules/@wot-ui/ui/components/wd-gap/wd-gap.js";
if (!Array) {
  const _component_wd_gap = __unplugin_components_0;
  const _component_wd_tabbar_item = __unplugin_components_1;
  const _component_wd_tabbar = __unplugin_components_2;
  const _component_wd_config_provider = __unplugin_components_3;
  (_component_wd_gap + _component_wd_tabbar_item + _component_wd_tabbar + _component_wd_config_provider)();
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
  __name: "tabbar",
  setup(__props) {
    const router = common_vendor.useRouter();
    const route = common_vendor.useRoute();
    const { theme, themeVars } = composables_useManualTheme.useManualTheme();
    const { activeTabbar, getTabbarItemValue, setTabbarItemActive, tabbarList } = composables_useTabbar.useTabbar();
    function handleTabbarChange({ value }) {
      setTabbarItemActive(value);
      router.pushTab({ name: value });
    }
    common_vendor.onMounted(() => {
      common_vendor.nextTick$1(() => {
        if (route.name && route.name !== activeTabbar.value.name) {
          setTabbarItemActive(route.name);
        }
      });
    });
    common_vendor.watch(() => route.name, (newName) => {
      if (newName && newName !== activeTabbar.value.name) {
        const tabNames = tabbarList.value.map((item) => item.name);
        if (tabNames.includes(newName)) {
          setTabbarItemActive(newName);
        }
      }
    });
    return (_ctx, _cache) => {
      return {
        a: common_vendor.p({
          ["safe-area-bottom"]: true,
          height: "var(--wot-tabbar-height, 50px)"
        }),
        b: common_vendor.f(common_vendor.unref(tabbarList), (item, index, i0) => {
          return {
            a: common_vendor.w(({
              active
            }, s1, i1) => {
              return {
                a: active ? item.activeIcon : item.inactiveIcon,
                b: active ? 1 : "",
                c: !active ? 1 : "",
                d: i1,
                e: s1
              };
            }, {
              name: "icon",
              path: "b[" + i0 + "].a",
              vueId: "2bba6f50-3-" + i0 + ",2bba6f50-2"
            }),
            b: index,
            c: "2bba6f50-3-" + i0 + ",2bba6f50-2",
            d: common_vendor.p({
              name: item.name,
              value: common_vendor.unref(getTabbarItemValue)(item.name),
              title: item.title
            })
          };
        }),
        c: common_vendor.o(handleTabbarChange),
        d: common_vendor.p({
          ["model-value"]: common_vendor.unref(activeTabbar).name,
          ["safe-area-inset-bottom"]: true,
          fixed: true
        }),
        e: common_vendor.unref(theme) === "dark" ? 1 : "",
        f: common_vendor.p({
          theme: common_vendor.unref(theme),
          ["theme-vars"]: common_vendor.unref(themeVars)
        })
      };
    };
  }
});
exports._sfc_main = _sfc_main;
