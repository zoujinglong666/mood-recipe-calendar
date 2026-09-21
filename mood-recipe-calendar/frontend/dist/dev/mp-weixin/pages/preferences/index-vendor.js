"use strict";
const common_vendor = require("../../common/vendor.js");
const utils_assets = require("../../utils/assets.js");
const composables_useNavBar = require("../../composables/useNavBar.js");
const api_preferences = require("../../api/preferences.js");
const utils_login = require("../../utils/login.js");
const utils_toast = require("../../utils/toast.js");
const __unplugin_components_0 = () => "../../node-modules/@wot-ui/ui/components/wd-navbar/wd-navbar.js";
if (!Array) {
  const _component_wd_navbar = __unplugin_components_0;
  const _component_layout_default_uni = common_vendor.resolveComponent("layout-default-uni");
  (_component_wd_navbar + _component_layout_default_uni)();
}
const _sfc_main = /* @__PURE__ */ common_vendor.defineComponent({
  __name: "index",
  setup(__props) {
    const route = common_vendor.useRoute();
    const router = common_vendor.useRouter();
    const loading = common_vendor.ref(true);
    const saving = common_vendor.ref(false);
    const favoriteTags = common_vendor.ref([]);
    const favoriteCuisines = common_vendor.ref([]);
    const favoriteDishes = common_vendor.ref("");
    const eatScallion = common_vendor.ref(null);
    const eatCilantro = common_vendor.ref(null);
    const spiceLevel = common_vendor.ref("NORMAL");
    const healthGoal = common_vendor.ref("BALANCED");
    const avoidIngredients = common_vendor.ref("");
    const allergens = common_vendor.ref("");
    const behavior = common_vendor.ref(null);
    const FAVORITES = ["家常菜", "汤粥", "面食", "米饭", "清淡", "香辣", "肉食", "海鲜"];
    const CUISINES = ["川菜", "湘菜", "粤菜", "江浙菜", "东北菜", "西北菜", "云贵菜", "日韩料理"];
    const SPICE_LEVELS = [
      { value: "NONE", label: "不吃辣" },
      { value: "MILD", label: "微辣" },
      { value: "NORMAL", label: "正常辣" },
      { value: "HOT", label: "很能吃辣" }
    ];
    const HEALTH_GOALS = [
      { value: "BALANCED", label: "保持均衡", hint: "一荤一素一主食，吃得完整就很好" },
      { value: "FITNESS", label: "健身增肌", hint: "优先优质蛋白、适量主食和蔬菜" },
      { value: "LEAN", label: "轻盈减脂", hint: "优先蔬菜、优质蛋白和少油做法" }
    ];
    const onboarding = common_vendor.computed(() => route.query.from === "onboarding");
    common_vendor.onLoad(async () => {
      try {
        await utils_login.ensureLogin();
        const memory = await api_preferences.fetchFoodMemory();
        const data = memory.explicit;
        behavior.value = memory.behavior;
        favoriteTags.value = data.favoriteTags ? data.favoriteTags.split(",").filter(Boolean) : [];
        favoriteCuisines.value = data.favoriteCuisines ? data.favoriteCuisines.split(",").filter(Boolean) : [];
        favoriteDishes.value = data.favoriteDishes || "";
        eatScallion.value = data.eatScallion;
        eatCilantro.value = data.eatCilantro;
        spiceLevel.value = data.spiceLevel || "NORMAL";
        healthGoal.value = data.healthGoal || "BALANCED";
        avoidIngredients.value = data.avoidIngredients || "";
        allergens.value = data.allergens || "";
      } catch (e) {
        utils_toast.toastError(e, "记忆加载失败，请重试");
      } finally {
        loading.value = false;
      }
    });
    function toggleFavorite(tag) {
      favoriteTags.value = favoriteTags.value.includes(tag) ? favoriteTags.value.filter((item) => item !== tag) : [...favoriteTags.value, tag];
    }
    function toggleCuisine(cuisine) {
      favoriteCuisines.value = favoriteCuisines.value.includes(cuisine) ? favoriteCuisines.value.filter((item) => item !== cuisine) : [...favoriteCuisines.value, cuisine];
    }
    async function save() {
      if (saving.value)
        return;
      saving.value = true;
      try {
        await api_preferences.saveFoodPreference({
          favoriteTags: favoriteTags.value.join(","),
          favoriteCuisines: favoriteCuisines.value.join(","),
          favoriteDishes: favoriteDishes.value.trim(),
          avoidIngredients: avoidIngredients.value.trim(),
          allergens: allergens.value.trim(),
          eatScallion: eatScallion.value,
          eatCilantro: eatCilantro.value,
          spiceLevel: spiceLevel.value,
          healthGoal: healthGoal.value
        });
        common_vendor.index.removeStorageSync("mrc_companion_message");
        common_vendor.index.setStorageSync("mrc_preference_onboarded", "1");
        utils_toast.toastSuccess("锅仔记住啦");
        setTimeout(finish, 450);
      } catch (e) {
        utils_toast.toastError(e, "保存失败，请重试");
      } finally {
        saving.value = false;
      }
    }
    function finish() {
      const mood = String(route.query.mood || "");
      if (onboarding.value && mood)
        router.replace({ name: "recipe", query: { mood } });
      else
        composables_useNavBar.navBack();
    }
    async function skip() {
      favoriteTags.value = [];
      favoriteCuisines.value = [];
      favoriteDishes.value = "";
      eatScallion.value = null;
      eatCilantro.value = null;
      spiceLevel.value = "NORMAL";
      healthGoal.value = "BALANCED";
      avoidIngredients.value = "";
      allergens.value = "";
      await save();
    }
    function clearMemory() {
      common_vendor.index.showModal({
        title: "清除锅仔的口味记忆？",
        content: "会清除口味、忌口和推荐反馈，不影响做菜记录。你以后可以重新告诉锅仔。",
        confirmText: "确认清除",
        confirmColor: "#C84B3A",
        success: async (result) => {
          if (!result.confirm)
            return;
          try {
            await api_preferences.clearFoodPreference();
            common_vendor.index.removeStorageSync("mrc_companion_message");
            common_vendor.index.removeStorageSync("mrc_preference_onboarded");
            favoriteTags.value = [];
            favoriteCuisines.value = [];
            favoriteDishes.value = "";
            eatScallion.value = null;
            eatCilantro.value = null;
            spiceLevel.value = "NORMAL";
            healthGoal.value = "BALANCED";
            avoidIngredients.value = "";
            allergens.value = "";
            behavior.value = null;
            utils_toast.toast("口味记忆已清除");
          } catch (e) {
            utils_toast.toastError(e, "清除失败，请重试");
          }
        }
      });
    }
    return (_ctx, _cache) => {
      var _a, _b, _c, _d;
      return common_vendor.e({
        a: common_vendor.o(common_vendor.unref(composables_useNavBar.navBack)),
        b: common_vendor.p({
          title: "锅仔记忆",
          ["left-arrow"]: true,
          ["safe-area-inset-top"]: true,
          ["custom-style"]: "background-color: transparent !important;"
        }),
        c: loading.value
      }, loading.value ? {} : common_vendor.e({
        d: common_vendor.unref(utils_assets.STATIC_BASE_URL) + "/static/guozai/action_10_thinking.png",
        e: behavior.value && (behavior.value.likedCount || behavior.value.dislikedCount || behavior.value.madeCount)
      }, behavior.value && (behavior.value.likedCount || behavior.value.dislikedCount || behavior.value.madeCount) ? {
        f: common_vendor.t(behavior.value.madeCount),
        g: common_vendor.t(behavior.value.likedCount),
        h: common_vendor.t(behavior.value.streak)
      } : {}, {
        i: ((_a = behavior.value) == null ? void 0 : _a.topDish) || ((_b = behavior.value) == null ? void 0 : _b.topMood)
      }, ((_c = behavior.value) == null ? void 0 : _c.topDish) || ((_d = behavior.value) == null ? void 0 : _d.topMood) ? common_vendor.e({
        j: behavior.value.topDish
      }, behavior.value.topDish ? {
        k: common_vendor.t(behavior.value.topDish)
      } : {}, {
        l: behavior.value.topMood
      }, behavior.value.topMood ? {
        m: common_vendor.t(behavior.value.topMood)
      } : {}) : {}, {
        n: common_vendor.f(FAVORITES, (tag, k0, i0) => {
          return {
            a: common_vendor.t(tag),
            b: tag,
            c: favoriteTags.value.includes(tag) ? 1 : "",
            d: favoriteTags.value.includes(tag),
            e: common_vendor.o(($event) => toggleFavorite(tag), tag)
          };
        }),
        o: favoriteDishes.value,
        p: common_vendor.o(($event) => favoriteDishes.value = $event.detail.value),
        q: common_vendor.f(CUISINES, (cuisine, k0, i0) => {
          return common_vendor.e({
            a: favoriteCuisines.value.includes(cuisine)
          }, favoriteCuisines.value.includes(cuisine) ? {} : {}, {
            b: common_vendor.t(cuisine),
            c: cuisine,
            d: favoriteCuisines.value.includes(cuisine) ? 1 : "",
            e: favoriteCuisines.value.includes(cuisine),
            f: common_vendor.o(($event) => toggleCuisine(cuisine), cuisine)
          });
        }),
        r: eatScallion.value === true ? 1 : "",
        s: common_vendor.o(($event) => eatScallion.value = true),
        t: eatScallion.value === false ? 1 : "",
        v: common_vendor.o(($event) => eatScallion.value = false),
        w: eatCilantro.value === true ? 1 : "",
        x: common_vendor.o(($event) => eatCilantro.value = true),
        y: eatCilantro.value === false ? 1 : "",
        z: common_vendor.o(($event) => eatCilantro.value = false),
        A: common_vendor.f(SPICE_LEVELS, (item, k0, i0) => {
          return {
            a: common_vendor.t(item.label),
            b: item.value,
            c: spiceLevel.value === item.value ? 1 : "",
            d: spiceLevel.value === item.value,
            e: common_vendor.o(($event) => spiceLevel.value = item.value, item.value)
          };
        }),
        B: common_vendor.f(HEALTH_GOALS, (goal, k0, i0) => {
          return {
            a: common_vendor.t(goal.label),
            b: common_vendor.t(goal.hint),
            c: common_vendor.t(healthGoal.value === goal.value ? "✓" : ""),
            d: goal.value,
            e: healthGoal.value === goal.value ? 1 : "",
            f: healthGoal.value === goal.value,
            g: common_vendor.o(($event) => healthGoal.value = goal.value, goal.value)
          };
        }),
        C: avoidIngredients.value,
        D: common_vendor.o(($event) => avoidIngredients.value = $event.detail.value),
        E: allergens.value,
        F: common_vendor.o(($event) => allergens.value = $event.detail.value),
        G: common_vendor.t(saving.value ? "正在记住…" : "让锅仔记住"),
        H: saving.value,
        I: common_vendor.o(save),
        J: onboarding.value
      }, onboarding.value ? {
        K: saving.value,
        L: common_vendor.o(skip)
      } : {
        M: saving.value,
        N: common_vendor.o(clearMemory)
      }));
    };
  }
});
const MiniProgramPage = /* @__PURE__ */ common_vendor._export_sfc(_sfc_main, [["__scopeId", "data-v-32f765e2"]]);
exports.MiniProgramPage = MiniProgramPage;
