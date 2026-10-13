"use strict";
const common_vendor = require("../../common/vendor.js");
const utils_assets = require("../../utils/assets.js");
const api_recipes = require("../../api/recipes.js");
const utils_cookingDraft = require("../../utils/cookingDraft.js");
const composables_useWishlist = require("../../composables/useWishlist.js");
const api_favorites = require("../../api/favorites.js");
const utils_toast = require("../../utils/toast.js");
const utils_login = require("../../utils/login.js");
const stores_user = require("../../stores/user.js");
const __unplugin_components_0 = () => "../../node-modules/@wot-ui/ui/components/wd-navbar/wd-navbar.js";
if (!Array) {
  const _component_wd_navbar = __unplugin_components_0;
  const _component_layout_default_uni = common_vendor.resolveComponent("layout-default-uni");
  (_component_wd_navbar + _component_layout_default_uni)();
}
const _sfc_main = /* @__PURE__ */ common_vendor.defineComponent({
  __name: "index",
  setup(__props) {
    const router = common_vendor.useRouter();
    const userStore = stores_user.useUserStore();
    const { toggle } = composables_useWishlist.useWishlist();
    const loading = common_vendor.ref(true);
    const items = common_vendor.ref([]);
    const loggedIn = common_vendor.computed(() => userStore.isLoggedIn);
    common_vendor.onShow(async () => {
      if (!userStore.isLoggedIn) {
        loading.value = false;
        items.value = [];
        return;
      }
      loading.value = true;
      try {
        items.value = await api_favorites.fetchFavorites();
      } catch (e) {
        utils_toast.toastError(e, "想吃清单加载失败");
      } finally {
        loading.value = false;
      }
    });
    async function openCook(item) {
      try {
        const recipe = await api_recipes.fetchRecipeDetail(item.recipeId);
        utils_cookingDraft.saveCookingDraft(recipe, "满足");
        router.push({ name: "cooking" });
      } catch (e) {
        utils_toast.toastError(e, "这道菜暂时打不开");
      }
    }
    async function onHeart(item) {
      const backup = items.value;
      items.value = items.value.filter((x) => x.recipeId !== item.recipeId);
      await toggle(String(item.recipeId));
      try {
        items.value = await api_favorites.fetchFavorites();
      } catch {
        items.value = backup;
        utils_toast.toast("取消收藏没成功，请稍后重试");
      }
    }
    function goLogin() {
      utils_login.navigateToLogin();
    }
    function goLibrary() {
      router.push({ name: "recipe-library" });
    }
    return (_ctx, _cache) => {
      return common_vendor.e({
        a: common_vendor.o(common_vendor.unref(router).back),
        b: common_vendor.p({
          title: "想吃清单",
          ["left-arrow"]: true,
          ["safe-area-inset-top"]: true,
          ["custom-style"]: "background-color: transparent !important;"
        }),
        c: loading.value
      }, loading.value ? {} : !loggedIn.value ? {
        e: `${common_vendor.unref(utils_assets.STATIC_BASE_URL)}/static/guozai/action_08_peek.png`,
        f: common_vendor.o(goLogin)
      } : !items.value.length ? {
        h: `${common_vendor.unref(utils_assets.STATIC_BASE_URL)}/static/guozai/action_08_peek.png`,
        i: common_vendor.o(goLibrary)
      } : {
        j: common_vendor.f(items.value, (r, k0, i0) => {
          return common_vendor.e({
            a: r.image || `${common_vendor.unref(utils_assets.STATIC_BASE_URL)}/static/guozai/action_01_bowl.png`,
            b: common_vendor.t(r.name),
            c: common_vendor.t(r.description),
            d: r.cookingTime
          }, r.cookingTime ? {
            e: common_vendor.t(r.cookingTime)
          } : {}, {
            f: r.difficulty
          }, r.difficulty ? {
            g: common_vendor.t(r.difficulty)
          } : {}, {
            h: common_vendor.o(($event) => onHeart(r), r.recipeId),
            i: r.recipeId,
            j: `查看${r.name}做法`,
            k: common_vendor.o(($event) => openCook(r), r.recipeId)
          });
        })
      }, {
        d: !loggedIn.value,
        g: !items.value.length
      });
    };
  }
});
const MiniProgramPage = /* @__PURE__ */ common_vendor._export_sfc(_sfc_main, [["__scopeId", "data-v-a396f15d"]]);
exports.MiniProgramPage = MiniProgramPage;
