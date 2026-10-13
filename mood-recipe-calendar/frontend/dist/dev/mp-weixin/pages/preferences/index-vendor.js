"use strict";
const common_vendor = require("../../common/vendor.js");
const api_auth = require("../../api/auth.js");
const composables_useNavBar = require("../../composables/useNavBar.js");
const utils_assets = require("../../utils/assets.js");
const utils_memoryLabels = require("../../utils/memoryLabels.js");
const api_preferences = require("../../api/preferences.js");
const utils_login = require("../../utils/login.js");
const utils_toast = require("../../utils/toast.js");
const __unplugin_components_3 = () => "../../node-modules/@wot-ui/ui/components/wd-collapse/wd-collapse.js";
const __unplugin_components_2 = () => "../../node-modules/@wot-ui/ui/components/wd-collapse-item/wd-collapse-item.js";
const __unplugin_components_1 = () => "../../node-modules/@wot-ui/ui/components/wd-switch/wd-switch.js";
const __unplugin_components_0 = () => "../../node-modules/@wot-ui/ui/components/wd-navbar/wd-navbar.js";
if (!Array) {
  const _component_wd_navbar = __unplugin_components_0;
  const _component_wd_switch = __unplugin_components_1;
  const _component_wd_collapse_item = __unplugin_components_2;
  const _component_wd_collapse = __unplugin_components_3;
  const _component_layout_default_uni = common_vendor.resolveComponent("layout-default-uni");
  (_component_wd_navbar + _component_wd_switch + _component_wd_collapse_item + _component_wd_collapse + _component_layout_default_uni)();
}
const _sfc_defineComponent = common_vendor.defineComponent({
  __name: "index",
  setup(__props) {
    common_vendor.onShareAppMessage(() => {
      var _a;
      const pages = getCurrentPages();
      const route2 = ((_a = pages[pages.length - 1]) == null ? void 0 : _a.route) || "";
      return { title: "锅仔 · 按心情帮你决定今天吃什么", path: `/${route2}` };
    });
    common_vendor.onShareTimeline(() => ({ title: "锅仔 · 按心情帮你决定今天吃什么" }));
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
    const learnedFacts = common_vendor.ref([]);
    const personalizationEnabled = common_vendor.ref(true);
    const collapsedFacts = common_vendor.ref([]);
    const CATEGORY_LABELS = {
      EXPLICIT_PREFERENCE: "你主动告诉锅仔的",
      BEHAVIOR_SIGNAL: "从选择和反馈学到的",
      MEAL_CONTEXT: "平时怎么吃饭",
      SHORT_TERM_STATE: "最近这段时间",
      SAFETY_CONSTRAINT: "安全与忌口"
    };
    const groupedFacts = common_vendor.computed(() => Object.entries(CATEGORY_LABELS).map(([category, label]) => ({
      category,
      label,
      facts: learnedFacts.value.filter((item) => item.category === category)
    })).filter((group) => group.facts.length));
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
        const agentMemory = await api_auth.getAgentMemory();
        learnedFacts.value = agentMemory.facts || [];
        personalizationEnabled.value = agentMemory.personalizationEnabled !== false;
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
    async function onPersonalizationChange(payload) {
      var _a;
      const next = typeof (payload == null ? void 0 : payload.value) === "boolean" ? payload.value : !!((_a = payload == null ? void 0 : payload.detail) == null ? void 0 : _a.value);
      try {
        personalizationEnabled.value = (await api_auth.setAgentPersonalization(next)).enabled;
      } catch (error) {
        personalizationEnabled.value = !next;
        utils_toast.toastError(error, "个性化设置失败");
      }
    }
    async function forgetFact(key) {
      try {
        await api_auth.forgetAgentMemory(key);
        learnedFacts.value = learnedFacts.value.filter((item) => item.key !== key);
        utils_toast.toastSuccess("好，锅仔已经忘掉这条了");
      } catch (error) {
        utils_toast.toastError(error, "暂时忘不掉，请稍后再试");
      }
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
        router.replace({ name: "recipe", params: { mood } });
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
            await Promise.all([api_preferences.clearFoodPreference(), api_auth.clearAgentMemory()]);
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
            learnedFacts.value = [];
            utils_toast.toast("好，锅仔都忘掉了");
          } catch (e) {
            utils_toast.toastError(e, "暂时忘不掉，请稍后再试");
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
        d: `${common_vendor.unref(utils_assets.STATIC_BASE_URL)}/static/guozai/action_10_thinking.png`,
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
        n: common_vendor.o(onPersonalizationChange),
        o: common_vendor.o(($event) => personalizationEnabled.value = $event),
        p: common_vendor.p({
          size: 32,
          ["active-color"]: "#EF5A3C",
          ["custom-class"]: "memory-switch",
          modelValue: personalizationEnabled.value
        }),
        q: !groupedFacts.value.length
      }, !groupedFacts.value.length ? {} : {
        r: common_vendor.f(groupedFacts.value, (group, k0, i0) => {
          return {
            a: common_vendor.t(group.label),
            b: common_vendor.t(group.facts.length),
            c: common_vendor.f(group.facts, (fact, k1, i1) => {
              return {
                a: common_vendor.t(common_vendor.unref(utils_memoryLabels.memoryKeyLabel)(fact.key)),
                b: common_vendor.t(common_vendor.unref(utils_memoryLabels.humanizeMemoryValue)(fact.key, fact.value)),
                c: common_vendor.t(common_vendor.unref(utils_memoryLabels.sourceLabel)(fact)),
                d: common_vendor.o(($event) => forgetFact(fact.key), fact.key),
                e: fact.key
              };
            }),
            d: group.category,
            e: "32f765e2-4-" + i0 + ",32f765e2-3",
            f: common_vendor.p({
              name: group.category,
              border: false,
              ["custom-class"]: "memory-facts__item"
            })
          };
        }),
        s: common_vendor.o(($event) => collapsedFacts.value = $event),
        t: common_vendor.p({
          ["custom-class"]: "memory-facts",
          modelValue: collapsedFacts.value
        })
      }, {
        v: common_vendor.f(FAVORITES, (tag, k0, i0) => {
          return {
            a: common_vendor.t(tag),
            b: tag,
            c: favoriteTags.value.includes(tag) ? 1 : "",
            d: favoriteTags.value.includes(tag),
            e: common_vendor.o(($event) => toggleFavorite(tag), tag)
          };
        }),
        w: favoriteDishes.value,
        x: common_vendor.o(($event) => favoriteDishes.value = $event.detail.value),
        y: common_vendor.f(CUISINES, (cuisine, k0, i0) => {
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
        z: eatScallion.value === true ? 1 : "",
        A: common_vendor.o(($event) => eatScallion.value = true),
        B: eatScallion.value === false ? 1 : "",
        C: common_vendor.o(($event) => eatScallion.value = false),
        D: eatCilantro.value === true ? 1 : "",
        E: common_vendor.o(($event) => eatCilantro.value = true),
        F: eatCilantro.value === false ? 1 : "",
        G: common_vendor.o(($event) => eatCilantro.value = false),
        H: common_vendor.f(SPICE_LEVELS, (item, k0, i0) => {
          return {
            a: common_vendor.t(item.label),
            b: item.value,
            c: spiceLevel.value === item.value ? 1 : "",
            d: spiceLevel.value === item.value,
            e: common_vendor.o(($event) => spiceLevel.value = item.value, item.value)
          };
        }),
        I: common_vendor.f(HEALTH_GOALS, (goal, k0, i0) => {
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
        J: avoidIngredients.value,
        K: common_vendor.o(($event) => avoidIngredients.value = $event.detail.value),
        L: allergens.value,
        M: common_vendor.o(($event) => allergens.value = $event.detail.value),
        N: common_vendor.t(saving.value ? "正在记住…" : "让锅仔记住"),
        O: saving.value,
        P: common_vendor.o(save),
        Q: onboarding.value
      }, onboarding.value ? {
        R: saving.value,
        S: common_vendor.o(skip)
      } : {
        T: saving.value,
        U: common_vendor.o(clearMemory)
      }));
    };
  }
});
_sfc_defineComponent.__runtimeHooks = 6;
const MiniProgramPage = /* @__PURE__ */ common_vendor._export_sfc(_sfc_defineComponent, [["__scopeId", "data-v-32f765e2"]]);
exports.MiniProgramPage = MiniProgramPage;
