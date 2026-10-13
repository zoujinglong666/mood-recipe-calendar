"use strict";
const common_vendor = require("../../common/vendor.js");
const utils_assets = require("../../utils/assets.js");
const api_themeAlbums = require("../../api/themeAlbums.js");
const api_recipes = require("../../api/recipes.js");
const utils_cookingDraft = require("../../utils/cookingDraft.js");
const composables_useVirtualPurchase = require("../../composables/useVirtualPurchase.js");
const utils_toast = require("../../utils/toast.js");
const __unplugin_components_0 = () => "../../node-modules/@wot-ui/ui/components/wd-navbar/wd-navbar.js";
if (!Array) {
  const _component_wd_navbar = __unplugin_components_0;
  const _component_layout_default_uni = common_vendor.resolveComponent("layout-default-uni");
  (_component_wd_navbar + _component_layout_default_uni)();
}
const _sfc_main = /* @__PURE__ */ common_vendor.defineComponent({
  __name: "detail",
  setup(__props) {
    const router = common_vendor.useRouter();
    const { purchasingSku, purchase } = composables_useVirtualPurchase.useVirtualPurchase();
    const loading = common_vendor.ref(true);
    const album = common_vendor.ref(null);
    const dishes = common_vendor.computed(() => {
      var _a;
      return ((_a = album.value) == null ? void 0 : _a.dishes) || [];
    });
    const priceText = common_vendor.computed(
      () => {
        var _a;
        return ((_a = album.value) == null ? void 0 : _a.priceFen) != null ? `¥${(album.value.priceFen / 100).toFixed(2)}` : "";
      }
    );
    common_vendor.onLoad(async (options) => {
      const sku = options == null ? void 0 : options.sku;
      if (!sku) {
        utils_toast.toast("专辑不存在");
        router.back();
        return;
      }
      loading.value = true;
      try {
        album.value = await api_themeAlbums.fetchThemeAlbumDetail(sku);
      } catch (e) {
        utils_toast.toastError(e, "专辑加载失败");
      } finally {
        loading.value = false;
      }
    });
    function buy() {
      if (!album.value || purchasingSku.value)
        return;
      purchase(album.value.sku, async () => {
        try {
          album.value = await api_themeAlbums.fetchThemeAlbumDetail(album.value.sku);
        } catch {
        }
      });
    }
    async function openDish(dish) {
      var _a;
      if (!((_a = album.value) == null ? void 0 : _a.owned)) {
        utils_toast.toast("购买后即可查看做法");
        return;
      }
      try {
        const recipe = await api_recipes.fetchRecipeDetail(dish.id);
        utils_cookingDraft.saveCookingDraft(recipe, "满足");
        router.push({ name: "cooking" });
      } catch (e) {
        utils_toast.toastError(e, "这道菜暂时打不开");
      }
    }
    return (_ctx, _cache) => {
      return common_vendor.e({
        a: common_vendor.o(common_vendor.unref(router).back),
        b: common_vendor.p({
          title: "主题专辑",
          ["left-arrow"]: true,
          ["safe-area-inset-top"]: true,
          ["custom-style"]: "background-color: transparent !important;"
        }),
        c: loading.value
      }, loading.value ? {} : album.value ? common_vendor.e({
        e: `${common_vendor.unref(utils_assets.STATIC_BASE_URL)}${album.value.cover}`,
        f: common_vendor.t(album.value.title),
        g: common_vendor.t(album.value.subtitle),
        h: album.value.owned
      }, album.value.owned ? {} : {}, {
        i: common_vendor.t(album.value.description),
        j: common_vendor.t(album.value.totalCount),
        k: !album.value.owned
      }, !album.value.owned ? {
        l: common_vendor.t(album.value.visibleCount)
      } : {}, {
        m: common_vendor.f(dishes.value, (dish, i, i0) => {
          return common_vendor.e({
            a: common_vendor.t(i + 1),
            b: dish.image || `${common_vendor.unref(utils_assets.STATIC_BASE_URL)}/static/guozai/action_01_bowl.png`,
            c: common_vendor.t(dish.name),
            d: common_vendor.t(dish.description),
            e: dish.cookingTime
          }, dish.cookingTime ? {
            f: common_vendor.t(dish.cookingTime)
          } : {}, {
            g: dish.difficulty
          }, dish.difficulty ? {
            h: common_vendor.t(dish.difficulty)
          } : {}, !album.value.owned ? {} : {}, {
            i: dish.id,
            j: album.value.owned ? `查看${dish.name}做法` : "购买后查看做法",
            k: common_vendor.o(($event) => openDish(dish), dish.id)
          });
        }),
        n: !album.value.owned,
        o: !album.value.owned ? 1 : "",
        p: !album.value.owned
      }, !album.value.owned ? {
        q: common_vendor.t(album.value.totalCount - album.value.visibleCount)
      } : {}, {
        r: !album.value.owned
      }, !album.value.owned ? {
        s: common_vendor.t(priceText.value),
        t: common_vendor.t(common_vendor.unref(purchasingSku) ? "处理中…" : "立即解锁"),
        v: Boolean(common_vendor.unref(purchasingSku)) ? 1 : "",
        w: common_vendor.o(buy)
      } : {}) : {
        x: `${common_vendor.unref(utils_assets.STATIC_BASE_URL)}/static/guozai/action_08_peek.png`
      }, {
        d: album.value
      });
    };
  }
});
const MiniProgramPage = /* @__PURE__ */ common_vendor._export_sfc(_sfc_main, [["__scopeId", "data-v-d1b74d09"]]);
exports.MiniProgramPage = MiniProgramPage;
