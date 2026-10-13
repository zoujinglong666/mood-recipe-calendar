"use strict";
const composables_types_theme = require("./types/theme.js");
const utils_systemTheme = require("../utils/systemTheme.js");
const store_manualThemeStore = require("../store/manualThemeStore.js");
const common_vendor = require("../common/vendor.js");
function useManualTheme() {
  const store = store_manualThemeStore.useManualThemeStore();
  const showThemeColorSheet = common_vendor.ref(false);
  let stopThemeChangeListener;
  function toggleTheme(mode, isFollowSystem = false) {
    store.toggleTheme(mode, isFollowSystem);
  }
  function openThemeColorPicker() {
    showThemeColorSheet.value = true;
  }
  function closeThemeColorPicker() {
    showThemeColorSheet.value = false;
  }
  function selectThemeColor(option) {
    store.setCurrentThemeColor(option);
    closeThemeColorPicker();
  }
  function initTheme() {
    store.initTheme();
  }
  common_vendor.onBeforeMount(() => {
    utils_systemTheme.initializeThemeOnce(store, initTheme);
    stopThemeChangeListener = utils_systemTheme.subscribeSystemThemeChange(store, (res) => {
      if (store.followSystem) {
        store.toggleTheme(res.theme, true);
      }
    });
  });
  common_vendor.onShow(() => {
    store.setNavigationBarColor();
  });
  common_vendor.onUnmounted(() => {
    stopThemeChangeListener == null ? void 0 : stopThemeChangeListener();
    stopThemeChangeListener = void 0;
  });
  return {
    // 状态
    theme: common_vendor.computed(() => store.theme),
    isDark: common_vendor.computed(() => store.isDark),
    followSystem: common_vendor.computed(() => store.followSystem),
    hasUserSet: common_vendor.computed(() => store.hasUserSet),
    currentThemeColor: common_vendor.computed(() => store.currentThemeColor),
    themeVars: common_vendor.computed(() => store.themeVars),
    showThemeColorSheet,
    // 常量
    themeColorOptions: composables_types_theme.themeColorOptions,
    // 方法
    initTheme,
    toggleTheme,
    setFollowSystem: store.setFollowSystem,
    openThemeColorPicker,
    closeThemeColorPicker,
    selectThemeColor
  };
}
exports.useManualTheme = useManualTheme;
