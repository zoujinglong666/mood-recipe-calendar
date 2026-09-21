"use strict";
Object.defineProperty(exports, Symbol.toStringTag, { value: "Module" });
const common_vendor = require("./common/vendor.js");
const composables_useNavBar = require("./composables/useNavBar.js");
const stores_user = require("./stores/user.js");
const router_index = require("./router/index.js");
const store_persist = require("./store/persist.js");
if (!Math) {
  "./pages/index/index.js";
  "./pages/about/index.js";
  "./pages/album/index.js";
  "./pages/calendar/index.js";
  "./pages/cooking/index.js";
  "./pages/feedback/index.js";
  "./pages/gallery/index.js";
  "./pages/login/index.js";
  "./pages/meal-agent/index.js";
  "./pages/membership/index.js";
  "./pages/mood/index.js";
  "./pages/preferences/index.js";
  "./pages/privacy/index.js";
  "./pages/profile/index.js";
  "./pages/recipe/index.js";
  "./pages/record/index.js";
  "./pages/report/index.js";
  "./pages/settings/index.js";
  "./pages/timeline/index.js";
  "./pages/weekly-plan/detail.js";
  "./pages/weekly-plan/index.js";
}
const __unplugin_components_0 = () => "./node-modules/@wot-ui/ui/components/wd-popup/wd-popup.js";
if (!Array) {
  const _component_wd_popup = __unplugin_components_0;
  _component_wd_popup();
}
const __default__ = {
  options: {
    virtualHost: true,
    addGlobalClass: true,
    styleIsolation: "shared"
  }
};
const _sfc_main$1 = /* @__PURE__ */ common_vendor.defineComponent({
  ...__default__,
  __name: "PrivacyPopup",
  props: {
    title: { default: "用户隐私保护提示" },
    desc: { default: "感谢您使用本应用，您使用本应用的服务之前请仔细阅读并同意" },
    subDesc: { default: "。当您点击同意并开始时用产品服务时，即表示你已理解并同意该条款内容，该条款将对您产生法律约束力。如您拒绝，将无法使用相应服务。" },
    protocol: { default: "《用户隐私保护指引》" }
  },
  emits: ["agree", "disagree"],
  setup(__props, { emit: __emit }) {
    const emit = __emit;
    const showPopup = common_vendor.ref(false);
    const privacyResolves = common_vendor.ref(/* @__PURE__ */ new Set());
    function privacyHandler(resolve) {
      showPopup.value = true;
      privacyResolves.value.add(resolve);
    }
    common_vendor.onBeforeMount(() => {
      if (common_vendor.wx$1.onNeedPrivacyAuthorization) {
        common_vendor.wx$1.onNeedPrivacyAuthorization((resolve) => {
          if (typeof privacyHandler === "function") {
            privacyHandler(resolve);
          }
        });
      }
    });
    function handleAgree() {
      showPopup.value = false;
      privacyResolves.value.forEach((resolve) => {
        resolve({
          event: "agree",
          buttonId: "agree-btn"
        });
      });
      privacyResolves.value.clear();
      emit("agree");
    }
    function handleDisagree() {
      showPopup.value = false;
      privacyResolves.value.forEach((resolve) => {
        resolve({
          event: "disagree"
        });
      });
      privacyResolves.value.clear();
    }
    function openPrivacyContract() {
      common_vendor.wx$1.openPrivacyContract({});
    }
    function handleClose() {
      privacyResolves.value.clear();
    }
    return (_ctx, _cache) => {
      return {
        a: common_vendor.t(_ctx.title),
        b: common_vendor.t(_ctx.desc),
        c: common_vendor.t(_ctx.protocol),
        d: common_vendor.o(openPrivacyContract),
        e: common_vendor.t(_ctx.subDesc),
        f: common_vendor.o(handleDisagree),
        g: common_vendor.o(handleAgree),
        h: common_vendor.o(handleClose),
        i: common_vendor.o(($event) => showPopup.value = $event),
        j: common_vendor.p({
          ["close-on-click-modal"]: false,
          ["custom-class"]: "wd-privacy-popup",
          modelValue: showPopup.value
        })
      };
    };
  }
});
const PrivacyPopup = /* @__PURE__ */ common_vendor._export_sfc(_sfc_main$1, [["__scopeId", "data-v-3b759913"]]);
if (!Math) {
  PrivacyPopup();
}
const _sfc_main = /* @__PURE__ */ common_vendor.defineComponent({
  __name: "App",
  setup(__props) {
    common_vendor.onLaunch(() => {
      composables_useNavBar.refreshNavMetrics();
      const userStore = stores_user.useUserStore();
      userStore.restoreFromStorage();
      common_vendor.index.$on("auth:expired", () => {
        userStore.logout();
        const pages = getCurrentPages();
        const current = pages[pages.length - 1];
        if ((current == null ? void 0 : current.route) !== "pages/login/index")
          common_vendor.index.reLaunch({ url: "/pages/login/index" });
      });
    });
    return (_ctx, _cache) => {
      return {};
    };
  }
});
const pinia = common_vendor.createPinia();
pinia.use(store_persist.persistPlugin);
function createApp() {
  const app = common_vendor.createSSRApp(_sfc_main);
  app.use(router_index.router);
  app.use(pinia);
  app.component("layout-default-uni", Layout_Default_Uni);
  app.component("layout-tabbar-uni", Layout_Tabbar_Uni);
  return {
    app
  };
}
const Layout_Default_Uni = () => "./layouts/default.js";
const Layout_Tabbar_Uni = () => "./layouts/tabbar.js";
createApp().app.mount("#app");
exports.createApp = createApp;
