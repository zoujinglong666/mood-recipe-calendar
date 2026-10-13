"use strict";
const common_vendor = require("../../common/vendor.js");
const api_auth = require("../../api/auth.js");
const api_request = require("../../api/request.js");
const composables_useManualTheme = require("../../composables/useManualTheme.js");
const composables_useNavBar = require("../../composables/useNavBar.js");
const stores_user = require("../../stores/user.js");
const utils_assets = require("../../utils/assets.js");
const utils_toast = require("../../utils/toast.js");
const utils_login = require("../../utils/login.js");
const utils_update = require("../../utils/update.js");
const __unplugin_components_0 = () => "../../node-modules/@wot-ui/ui/components/wd-navbar/wd-navbar.js";
if (!Array) {
  const _component_wd_navbar = __unplugin_components_0;
  const _component_layout_default_uni = common_vendor.resolveComponent("layout-default-uni");
  (_component_wd_navbar + _component_layout_default_uni)();
}
if (!Math) {
  Icon();
}
const Icon = () => "../../components/common/Icon.js";
const _sfc_defineComponent = common_vendor.defineComponent({
  __name: "index",
  setup(__props) {
    common_vendor.onShareAppMessage(() => {
      var _a;
      const pages = getCurrentPages();
      const route = ((_a = pages[pages.length - 1]) == null ? void 0 : _a.route) || "";
      return { title: "锅仔 · 按心情帮你决定今天吃什么", path: `/${route}` };
    });
    common_vendor.onShareTimeline(() => ({ title: "锅仔 · 按心情帮你决定今天吃什么" }));
    const THEME_CHOICES = [
      { value: "system", label: "跟随系统", icon: "phone" },
      { value: "light", label: "浅色", icon: "user" },
      { value: "dark", label: "深色", icon: "moon" }
    ];
    const userStore = stores_user.useUserStore();
    const router = common_vendor.useRouter();
    const { isDark, followSystem, currentThemeColor, themeColorOptions, toggleTheme, setFollowSystem, selectThemeColor } = composables_useManualTheme.useManualTheme();
    const nickname = common_vendor.ref("");
    const avatarUpdating = common_vendor.ref(false);
    const nicknameSaving = common_vendor.ref(false);
    const logoutLoading = common_vendor.ref(false);
    const deleteLoading = common_vendor.ref(false);
    const avatar = common_vendor.computed(() => {
      var _a;
      return ((_a = userStore.userInfo) == null ? void 0 : _a.avatarUrl) || `${utils_assets.STATIC_BASE_URL}/static/guozai/mood_01_happy.png`;
    });
    const themeChoice = common_vendor.computed(() => followSystem.value ? "system" : isDark.value ? "dark" : "light");
    function checkForUpdate() {
      utils_update.manualCheckUpdate();
    }
    const currentBackendUrl = common_vendor.ref(api_request.getApiBaseUrl());
    const currentBackend = common_vendor.computed(() => api_request.API_BACKENDS.find((b) => b.url === currentBackendUrl.value) || {
      name: "自定义",
      url: currentBackendUrl.value,
      env: "custom",
      desc: currentBackendUrl.value
    });
    const switchingBackend = common_vendor.ref(false);
    const showBackendSwitcher = true;
    function selectBackend(backend) {
      if (switchingBackend.value || backend.url === api_request.getApiBaseUrl())
        return;
      common_vendor.index.showModal({
        title: "切换后端服务",
        content: `将切换到「${backend.name}」（${backend.desc}）。切换后需要重新登录。`,
        confirmText: "切换",
        confirmColor: "#EF5A3C",
        success: (result) => {
          if (!result.confirm)
            return;
          switchingBackend.value = true;
          try {
            api_request.setApiBaseUrl(backend.url);
            currentBackendUrl.value = backend.url;
            userStore.logout();
            utils_toast.toastSuccess(`已切换到「${backend.name}」`);
          } finally {
            switchingBackend.value = false;
          }
        }
      });
    }
    common_vendor.onShow(async () => {
      var _a;
      userStore.restoreFromStorage();
      if (userStore.isLoggedIn) {
        await utils_login.refreshUserInfo();
      }
      nickname.value = ((_a = userStore.userInfo) == null ? void 0 : _a.nickname) || "";
    });
    async function updateAvatar(filePath) {
      if (!userStore.isLoggedIn) {
        utils_login.navigateToLogin();
        return;
      }
      if (!filePath || avatarUpdating.value)
        return;
      avatarUpdating.value = true;
      try {
        const uploaded = await api_request.uploadFile(filePath, "avatar");
        await api_auth.updateUserInfo({ openid: userStore.openid, avatarUrl: uploaded.url });
        await utils_login.refreshUserInfo(true);
        utils_toast.toastSuccess("头像已更新");
      } catch (error) {
        utils_toast.toastError(error, "头像更新失败，请重试");
      } finally {
        avatarUpdating.value = false;
      }
    }
    function onChooseAvatar(event) {
      var _a;
      updateAvatar(((_a = event.detail) == null ? void 0 : _a.avatarUrl) || "");
    }
    async function saveNickname() {
      var _a, _b, _c;
      const value = nickname.value.trim();
      if (!userStore.isLoggedIn) {
        utils_login.navigateToLogin();
        return;
      }
      if (!value) {
        utils_toast.toast("昵称不能为空");
        return;
      }
      if (nicknameSaving.value || value === ((_a = userStore.userInfo) == null ? void 0 : _a.nickname))
        return;
      nicknameSaving.value = true;
      try {
        await api_auth.updateUserInfo({ openid: userStore.openid, nickname: value });
        await utils_login.refreshUserInfo(true);
        nickname.value = ((_b = userStore.userInfo) == null ? void 0 : _b.nickname) || value;
        utils_toast.toastSuccess("昵称已保存");
      } catch (error) {
        nickname.value = ((_c = userStore.userInfo) == null ? void 0 : _c.nickname) || "";
        utils_toast.toastError(error, "昵称保存失败，请重试");
      } finally {
        nicknameSaving.value = false;
      }
    }
    function chooseTheme(value) {
      if (value === "system") {
        setFollowSystem(true);
        return;
      }
      setFollowSystem(false);
      toggleTheme(value);
    }
    function askForLogout() {
      if (!userStore.isLoggedIn || logoutLoading.value)
        return;
      common_vendor.index.showModal({
        title: "退出登录？",
        content: "退出后本机将清除你的登录信息，下次使用需要重新连接微信身份。",
        confirmText: "退出登录",
        confirmColor: "#D94841",
        success: (result) => {
          if (result.confirm)
            performLogout();
        }
      });
    }
    async function performLogout() {
      logoutLoading.value = true;
      try {
        await api_auth.logout().catch(() => {
        });
      } finally {
        userStore.logout();
        nickname.value = "";
        logoutLoading.value = false;
        utils_toast.toastSuccess("已退出登录");
      }
    }
    function askDeleteAccount() {
      if (deleteLoading.value)
        return;
      common_vendor.index.showModal({
        title: "永久注销账号？",
        content: "这会删除菜谱记录、图片、偏好和锅仔记忆，且无法恢复。依法需保留的订单会去标识化保存。",
        confirmText: "永久注销",
        confirmColor: "#D94841",
        success: async (result) => {
          if (!result.confirm)
            return;
          deleteLoading.value = true;
          try {
            await api_auth.deleteAccount();
            userStore.logout();
            nickname.value = "";
            utils_toast.toastSuccess("账号已注销");
            setTimeout(() => common_vendor.index.reLaunch({ url: "/pages/profile/index" }), 500);
          } catch (error) {
            utils_toast.toastError(error, "注销失败，数据尚未删除，请重试");
          } finally {
            deleteLoading.value = false;
          }
        }
      });
    }
    return (_ctx, _cache) => {
      var _a, _b;
      return common_vendor.e({
        a: common_vendor.o(common_vendor.unref(composables_useNavBar.navBack)),
        b: common_vendor.p({
          title: "设置",
          ["left-arrow"]: true,
          ["safe-area-inset-top"]: true,
          ["custom-style"]: "background-color: transparent !important;"
        }),
        c: `${common_vendor.unref(utils_assets.STATIC_BASE_URL)}/static/guozai/action_06_glasses.png`,
        d: common_vendor.unref(userStore).isLoggedIn
      }, common_vendor.unref(userStore).isLoggedIn ? {
        e: common_vendor.o(($event) => common_vendor.unref(router).push({
          name: "preferences"
        }))
      } : {}, {
        f: common_vendor.unref(userStore).isLoggedIn
      }, common_vendor.unref(userStore).isLoggedIn ? common_vendor.e({
        g: avatar.value,
        h: ((_a = common_vendor.unref(userStore).userInfo) == null ? void 0 : _a.avatarUrl) ? "aspectFill" : "aspectFit",
        i: common_vendor.p({
          name: "camera",
          size: 26,
          color: "#EF5A3C"
        }),
        j: avatarUpdating.value
      }, avatarUpdating.value ? {} : {}, {
        k: avatarUpdating.value,
        l: common_vendor.o(onChooseAvatar),
        m: common_vendor.o(saveNickname),
        n: nickname.value,
        o: common_vendor.o(($event) => nickname.value = $event.detail.value),
        p: common_vendor.t(nicknameSaving.value ? "保存中…" : "保存昵称"),
        q: nicknameSaving.value || !nickname.value.trim() || nickname.value.trim() === ((_b = common_vendor.unref(userStore).userInfo) == null ? void 0 : _b.nickname) ? 1 : "",
        r: common_vendor.o(saveNickname)
      }) : {
        s: `${common_vendor.unref(utils_assets.STATIC_BASE_URL)}/static/guozai/action_08_peek.png`
      }, {
        t: common_vendor.f(THEME_CHOICES, (option, k0, i0) => {
          return {
            a: "b4180827-3-" + i0 + ",b4180827-0",
            b: common_vendor.p({
              name: option.icon,
              size: 34,
              color: themeChoice.value === option.value ? "#EF5A3C" : "#A1826A"
            }),
            c: common_vendor.t(option.label),
            d: common_vendor.t(themeChoice.value === option.value ? "✓" : ""),
            e: option.value,
            f: themeChoice.value === option.value ? 1 : "",
            g: `切换为${option.label}`,
            h: common_vendor.o(($event) => chooseTheme(option.value), option.value)
          };
        }),
        v: common_vendor.f(common_vendor.unref(themeColorOptions), (option, k0, i0) => {
          var _a2;
          return {
            a: option.primary,
            b: common_vendor.t(option.name),
            c: option.value,
            d: ((_a2 = common_vendor.unref(currentThemeColor)) == null ? void 0 : _a2.value) === option.value ? 1 : "",
            e: `使用${option.name}主题色`,
            f: common_vendor.o(($event) => common_vendor.unref(selectThemeColor)(option), option.value)
          };
        }),
        w: common_vendor.unref(showBackendSwitcher)
      }, common_vendor.unref(showBackendSwitcher) ? {
        x: common_vendor.p({
          name: "gear",
          size: 30,
          color: "#EF5A3C"
        }),
        y: common_vendor.t(currentBackend.value.name),
        z: common_vendor.t(currentBackend.value.url),
        A: common_vendor.f(common_vendor.unref(api_request.API_BACKENDS), (option, k0, i0) => {
          return {
            a: common_vendor.t(option.name),
            b: common_vendor.t(option.desc),
            c: common_vendor.t(currentBackendUrl.value === option.url ? "✓" : ""),
            d: option.url,
            e: currentBackendUrl.value === option.url ? 1 : "",
            f: `切换到${option.name}`,
            g: common_vendor.o(($event) => selectBackend(option), option.url)
          };
        })
      } : {}, {
        B: common_vendor.p({
          name: "info",
          size: 30,
          color: "#EF5A3C"
        }),
        C: common_vendor.o(checkForUpdate),
        D: common_vendor.unref(userStore).isLoggedIn
      }, common_vendor.unref(userStore).isLoggedIn ? {
        E: common_vendor.t(logoutLoading.value ? "正在退出…" : "退出登录"),
        F: logoutLoading.value ? 1 : "",
        G: common_vendor.o(askForLogout)
      } : {}, {
        H: common_vendor.unref(userStore).isLoggedIn
      }, common_vendor.unref(userStore).isLoggedIn ? {
        I: common_vendor.t(deleteLoading.value ? "正在注销…" : "永久注销账号"),
        J: deleteLoading.value ? 1 : "",
        K: common_vendor.o(askDeleteAccount)
      } : {});
    };
  }
});
_sfc_defineComponent.__runtimeHooks = 6;
const MiniProgramPage = /* @__PURE__ */ common_vendor._export_sfc(_sfc_defineComponent, [["__scopeId", "data-v-b4180827"]]);
exports.MiniProgramPage = MiniProgramPage;
