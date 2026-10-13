"use strict";
const common_vendor = require("../common/vendor.js");
const utils_assets = require("../utils/assets.js");
const tabbarItems = common_vendor.ref([
  {
    name: "home",
    active: true,
    title: "首页",
    icon: "home",
    activeIcon: utils_assets.STATIC_BASE_URL + "/static/guozai/action_01_bowl.png",
    inactiveIcon: utils_assets.STATIC_BASE_URL + "/static/guozai/action_01_bowl.png"
  },
  {
    name: "record",
    active: false,
    title: "记录",
    icon: "camera",
    activeIcon: utils_assets.STATIC_BASE_URL + "/static/guozai/action_03_camera.png",
    inactiveIcon: utils_assets.STATIC_BASE_URL + "/static/guozai/action_03_camera.png"
  },
  {
    name: "profile",
    active: false,
    title: "我的",
    icon: "user",
    activeIcon: utils_assets.STATIC_BASE_URL + "/static/guozai/action_06_glasses.png",
    inactiveIcon: utils_assets.STATIC_BASE_URL + "/static/guozai/action_06_glasses.png"
  }
]);
function useTabbar() {
  const tabbarList = common_vendor.computed(() => tabbarItems.value);
  const activeTabbar = common_vendor.computed(() => {
    const item = tabbarItems.value.find((item2) => item2.active);
    return item || tabbarItems.value[0];
  });
  const getTabbarItemValue = (name) => {
    const item = tabbarItems.value.find((item2) => item2.name === name);
    return item == null ? void 0 : item.value;
  };
  const setTabbarItem = (name, value) => {
    const tabbarItem = tabbarItems.value.find((item) => item.name === name);
    if (tabbarItem) {
      tabbarItem.value = value;
    }
  };
  const setTabbarItemActive = (name) => {
    tabbarItems.value.forEach((item) => {
      if (item.name === name) {
        item.active = true;
      } else {
        item.active = false;
      }
    });
  };
  return {
    tabbarList,
    activeTabbar,
    getTabbarItemValue,
    setTabbarItem,
    setTabbarItemActive
  };
}
exports.useTabbar = useTabbar;
