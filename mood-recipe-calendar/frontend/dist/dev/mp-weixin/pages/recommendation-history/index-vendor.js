"use strict";
const common_vendor = require("../../common/vendor.js");
const api_recipes = require("../../api/recipes.js");
const utils_login = require("../../utils/login.js");
const utils_toast = require("../../utils/toast.js");
const utils_assets = require("../../utils/assets.js");
const utils_cookingDraft = require("../../utils/cookingDraft.js");
const __unplugin_components_0 = () => "../../node-modules/@wot-ui/ui/components/wd-navbar/wd-navbar.js";
if (!Array) {
  const _component_wd_navbar = __unplugin_components_0;
  const _component_layout_default_uni = common_vendor.resolveComponent("layout-default-uni");
  (_component_wd_navbar + _component_layout_default_uni)();
}
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
    const router = common_vendor.useRouter();
    const loading = common_vendor.ref(true);
    const items = common_vendor.ref([]);
    const grouped = common_vendor.computed(() => items.value.reduce((result, item) => {
      var _a;
      const key = ((_a = item.recommendedAt) == null ? void 0 : _a.slice(0, 10)) || "更早以前";
      (result[key] || (result[key] = [])).push(item);
      return result;
    }, {}));
    function dateText(value) {
      return value ? value.replace("T", " ").slice(0, 16) : "刚刚推荐";
    }
    function open(item) {
      router.push({ name: "recipe", params: { recipeId: String(item.id) } });
    }
    function cook(item) {
      utils_cookingDraft.saveCookingDraft({ ...item, description: item.description || "", cookingTime: item.cookingTime || 30, difficulty: item.difficulty || "简单", source: item.source === "AI" ? "AI" : "LOCAL", moodTags: "", season: "" }, "满足");
      router.push({ name: "cooking" });
    }
    async function load() {
      loading.value = true;
      try {
        await utils_login.ensureLogin();
        items.value = await api_recipes.fetchRecommendationHistory();
      } catch (error) {
        utils_toast.toastError(error, "推荐记录加载失败，请重试");
      } finally {
        loading.value = false;
      }
    }
    common_vendor.onShow(load);
    return (_ctx, _cache) => {
      return common_vendor.e({
        a: common_vendor.o(common_vendor.unref(router).back),
        b: common_vendor.p({
          title: "推荐记录",
          ["left-arrow"]: true,
          ["safe-area-inset-top"]: true,
          ["custom-style"]: "background-color: transparent !important;"
        }),
        c: loading.value
      }, loading.value ? {} : !items.value.length ? {
        e: `${common_vendor.unref(utils_assets.STATIC_BASE_URL)}/static/guozai/action_08_peek.png`
      } : {
        f: common_vendor.f(grouped.value, (dayItems, day, i0) => {
          return {
            a: common_vendor.t(day),
            b: common_vendor.f(dayItems, (item, k1, i1) => {
              return {
                a: item.image || `${common_vendor.unref(utils_assets.STATIC_BASE_URL)}/static/guozai/action_01_bowl.png`,
                b: common_vendor.t(item.source === "AI" ? "锅仔智能推荐" : "菜谱库推荐"),
                c: common_vendor.t(item.name),
                d: common_vendor.t(dateText(item.recommendedAt)),
                e: common_vendor.t(item.cookingTime || 30),
                f: `查看${item.name}`,
                g: common_vendor.o(($event) => open(item), `${item.id}-${item.recommendedAt}`),
                h: common_vendor.o(($event) => cook(item), `${item.id}-${item.recommendedAt}`),
                i: `${item.id}-${item.recommendedAt}`
              };
            }),
            c: day
          };
        })
      }, {
        d: !items.value.length
      });
    };
  }
});
_sfc_defineComponent.__runtimeHooks = 6;
const MiniProgramPage = /* @__PURE__ */ common_vendor._export_sfc(_sfc_defineComponent, [["__scopeId", "data-v-0fe881ac"]]);
exports.MiniProgramPage = MiniProgramPage;
