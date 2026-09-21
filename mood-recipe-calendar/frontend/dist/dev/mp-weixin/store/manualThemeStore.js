"use strict";
const common_vendor = require("../common/vendor.js");
const composables_types_theme = require("../composables/types/theme.js");
const utils_systemTheme = require("../utils/systemTheme.js");
function buildThemeVars(color) {
  return {
    ...color.primaryShades
  };
}
const useManualThemeStore = common_vendor.defineStore("manualTheme", {
  state: () => ({
    theme: "light",
    followSystem: true,
    // 是否跟随系统主题
    hasUserSet: false,
    // 用户是否手动设置过主题
    currentThemeColor: composables_types_theme.themeColorOptions[0],
    themeVars: buildThemeVars(composables_types_theme.themeColorOptions[0])
  }),
  getters: {
    isDark: (state) => state.theme === "dark"
  },
  actions: {
    /**
     * 手动切换主题
     * @param mode 指定主题模式，不传则自动切换
     * @param isFollowSystem 是否是跟随系统
     */
    toggleTheme(mode, isFollowSystem = false) {
      this.theme = mode || (this.theme === "light" ? "dark" : "light");
      if (!isFollowSystem) {
        this.hasUserSet = true;
        this.followSystem = false;
      }
      this.setNavigationBarColor();
    },
    /**
     * 设置是否跟随系统主题
     * @param follow 是否跟随系统
     */
    setFollowSystem(follow) {
      this.followSystem = follow;
      if (follow) {
        this.hasUserSet = false;
        this.initTheme();
      } else {
        this.hasUserSet = true;
        this.setNavigationBarColor();
      }
    },
    /**
     * 设置导航栏颜色
     */
    setNavigationBarColor() {
      common_vendor.index.setNavigationBarColor({
        frontColor: this.theme === "light" ? "#000000" : "#ffffff",
        backgroundColor: this.theme === "light" ? "#ffffff" : "#000000"
      });
    },
    /**
     * 设置主题色
     * @param color 主题色选项
     */
    setCurrentThemeColor(color) {
      this.currentThemeColor = color;
      this.themeVars = {
        ...this.themeVars,
        ...buildThemeVars(color)
      };
    },
    /**
     * 获取系统主题
     * @returns 系统主题模式
     */
    getSystemTheme() {
      return utils_systemTheme.getSystemTheme();
    },
    /**
     * 初始化主题
     */
    initTheme() {
      if (this.hasUserSet && !this.followSystem) {
        console.log("使用用户设置的主题:", this.theme);
        this.setNavigationBarColor();
        return;
      }
      const systemTheme = this.getSystemTheme();
      if (!this.hasUserSet || this.followSystem) {
        this.theme = systemTheme;
        if (!this.hasUserSet) {
          this.followSystem = true;
          console.log("首次启动，使用系统主题:", this.theme);
        } else {
          console.log("跟随系统主题:", this.theme);
        }
      }
      this.setNavigationBarColor();
    }
  }
});
exports.useManualThemeStore = useManualThemeStore;
