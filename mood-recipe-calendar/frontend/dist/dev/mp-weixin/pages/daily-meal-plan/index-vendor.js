"use strict";
const common_vendor = require("../../common/vendor.js");
const composables_useNavBar = require("../../composables/useNavBar.js");
const api_dailyMealPlan = require("../../api/dailyMealPlan.js");
const api_dailyMenu = require("../../api/dailyMenu.js");
const utils_assets = require("../../utils/assets.js");
const utils_albumShare = require("../../utils/albumShare.js");
const utils_toast = require("../../utils/toast.js");
const __unplugin_components_1 = () => "../../node-modules/@wot-ui/ui/components/wd-loading/wd-loading.js";
const __unplugin_components_0 = () => "../../node-modules/@wot-ui/ui/components/wd-button/wd-button.js";
if (!Array) {
  const _component_wd_button = __unplugin_components_0;
  const _component_wd_loading = __unplugin_components_1;
  const _component_layout_default_uni = common_vendor.resolveComponent("layout-default-uni");
  (_component_wd_button + _component_wd_loading + _component_layout_default_uni)();
}
const _sfc_defineComponent = common_vendor.defineComponent({
  __name: "index",
  setup(__props) {
    const nav = composables_useNavBar.useNavBar();
    const topInset = common_vendor.computed(() => `${nav.statusBarHeight + nav.navBarHeight + 12}px`);
    const WEEK = ["周日", "周一", "周二", "周三", "周四", "周五", "周六"];
    function formatPlanDate(iso) {
      if (!iso)
        return "";
      const d = /* @__PURE__ */ new Date(`${iso}T00:00:00`);
      if (Number.isNaN(d.getTime()))
        return iso;
      return `${d.getMonth() + 1}月${d.getDate()}日 · ${WEEK[d.getDay()]}`;
    }
    const plan = common_vendor.ref(null);
    const error = common_vendor.ref("");
    const expanded = common_vendor.reactive([false, false, false]);
    const replacing = common_vendor.reactive([false, false, false]);
    const swapping = common_vendor.reactive([false, false, false]);
    const generating = common_vendor.ref(false);
    function pulseSwap(i) {
      swapping[i] = true;
      setTimeout(() => {
        swapping[i] = false;
      }, 520);
    }
    const sharing = common_vendor.ref(false);
    const MEAL_LABELS = ["早餐", "午餐", "晚餐"];
    const MEAL_EN = ["BREAKFAST", "LUNCH", "DINNER"];
    function mealLabel(i) {
      return MEAL_LABELS[i] || "餐";
    }
    const CHECK_ITEMS = [
      { key: "谷薯类", label: "主食粗细搭配了" },
      { key: "优质蛋白", label: "蛋白质吃够了" },
      { key: "蔬菜", label: "深色蔬菜上桌了" },
      { key: "奶类", label: "奶或豆浆安排了" }
    ];
    const nutritionChecklist = common_vendor.computed(() => {
      var _a, _b;
      const tags = ((_b = (_a = plan.value) == null ? void 0 : _a.nutrition) == null ? void 0 : _b.tags) || [];
      const rows = CHECK_ITEMS.map((item) => ({ key: item.key, label: item.label, done: tags.includes(item.key) }));
      if (tags.includes("大豆及制品"))
        rows.push({ key: "大豆及制品", label: "豆制品有一席", done: true });
      if (tags.includes("水果"))
        rows.push({ key: "水果", label: "水果也安排了", done: true });
      return rows;
    });
    function mealImage(meal) {
      return meal.image || `${utils_assets.STATIC_BASE_URL}/static/guozai/action_01_bowl.png`;
    }
    function mealParts(name) {
      return api_dailyMenu.splitComboDishName(name);
    }
    function guidelineFor(i) {
      var _a, _b;
      const p = (_b = (_a = plan.value) == null ? void 0 : _a.guidelineBasis) == null ? void 0 : _b[i];
      return p && (p.title || p.content) ? p : null;
    }
    function list(raw) {
      if (Array.isArray(raw))
        return raw.map(String);
      if (typeof raw === "string" && raw.trim()) {
        try {
          const parsed = JSON.parse(raw);
          if (Array.isArray(parsed))
            return parsed.map(String);
        } catch {
        }
        return raw.split(/[,，;；\n]+/).map((s) => s.trim()).filter(Boolean);
      }
      return [];
    }
    function ingredientsFor(meal) {
      return list(meal.ingredients).slice(0, 12);
    }
    function stepsFor(meal) {
      return list(meal.steps).slice(0, 8);
    }
    function toggle(i) {
      expanded[i] = !expanded[i];
    }
    async function load() {
      var _a;
      error.value = "";
      utils_toast.showLoading("锅仔搭配中…");
      try {
        const data = await api_dailyMealPlan.fetchDailyMealPlan();
        if (!((_a = data == null ? void 0 : data.meals) == null ? void 0 : _a.length)) {
          error.value = "暂时没有可用的菜谱，稍后再来看看吧";
          return;
        }
        plan.value = data;
        expanded[0] = expanded[1] = expanded[2] = false;
        backfillCovers();
      } catch (e) {
        error.value = "加载失败，请检查网络后重试";
        utils_toast.toastError(e, "一日三餐加载失败");
      } finally {
        utils_toast.hideLoading();
      }
    }
    const backfilling = common_vendor.ref(false);
    async function backfillCovers() {
      var _a, _b, _c;
      if (!plan.value || backfilling.value)
        return;
      backfilling.value = true;
      try {
        const date = plan.value.date;
        for (let i = 0; i < plan.value.meals.length; i++) {
          if ((_a = plan.value.meals[i]) == null ? void 0 : _a.image)
            continue;
          const res = await api_dailyMealPlan.generateDailyMealCover(i, date);
          const updated = (_b = res == null ? void 0 : res.meals) == null ? void 0 : _b[i];
          if ((updated == null ? void 0 : updated.image) && plan.value && ((_c = plan.value.meals[i]) == null ? void 0 : _c.name) === updated.name) {
            plan.value.meals[i] = { ...plan.value.meals[i], image: updated.image };
          }
        }
      } catch {
      } finally {
        backfilling.value = false;
      }
    }
    async function replaceMeal(i) {
      var _a;
      if (!plan.value || replacing[i])
        return;
      replacing[i] = true;
      try {
        const res = await api_dailyMealPlan.replaceDailyMealPlanMeal(i, plan.value.date);
        if ((_a = res == null ? void 0 : res.meals) == null ? void 0 : _a.length) {
          plan.value = res;
          pulseSwap(i);
          utils_toast.toast("已为你换一道");
          backfillCovers();
        }
      } catch (e) {
        utils_toast.toastError(e, "换菜失败");
      } finally {
        replacing[i] = false;
      }
    }
    async function reshuffle() {
      var _a;
      if (!plan.value || generating.value)
        return;
      generating.value = true;
      utils_toast.showLoading("重新规划中…");
      try {
        const date = plan.value.date;
        for (let i = 0; i < 3; i++) {
          const res = await api_dailyMealPlan.replaceDailyMealPlanMeal(i, date);
          if ((_a = res == null ? void 0 : res.meals) == null ? void 0 : _a.length)
            plan.value = res;
        }
        for (let i = 0; i < 3; i++)
          pulseSwap(i);
        utils_toast.toastSuccess("已换一整天的搭配");
      } catch (e) {
        utils_toast.toastError(e, "重新规划失败");
      } finally {
        generating.value = false;
        utils_toast.hideLoading();
      }
    }
    async function share() {
      if (!plan.value || sharing.value)
        return;
      sharing.value = true;
      utils_toast.showLoading("生成分享图…");
      try {
        const input = {
          date: plan.value.date,
          meals: plan.value.meals.map((m, i) => ({
            name: m.name,
            mealLabel: mealLabel(i),
            image: mealImage(m),
            guideline: guidelineFor(i) || void 0
          })),
          nutrition: plan.value.nutrition || { headline: "", tags: [], tip: "" }
        };
        const path = await utils_albumShare.exportDailyMenuShare(input);
        await utils_albumShare.saveShareImage(path);
        utils_toast.toastSuccess("已保存到相册");
      } catch (e) {
        utils_toast.toastError(e, "分享图生成失败");
      } finally {
        sharing.value = false;
        utils_toast.hideLoading();
      }
    }
    function talkToGuozai() {
      common_vendor.index.navigateTo({ url: "/pages/meal-agent/index" });
    }
    common_vendor.onLoad(() => load());
    common_vendor.onShow(() => {
      if (!plan.value && !error.value)
        load();
    });
    common_vendor.onShareAppMessage(() => ({
      title: "锅仔替我安排了一日三餐，依据《中国居民膳食指南》",
      path: "/pages/daily-meal-plan/index"
    }));
    common_vendor.onShareTimeline(() => ({
      title: "锅仔的一日三餐 · 有图有依据"
    }));
    return (_ctx, _cache) => {
      return common_vendor.e({
        a: error.value
      }, error.value ? {
        b: common_vendor.t(error.value),
        c: common_vendor.o(load),
        d: common_vendor.p({
          type: "primary"
        })
      } : plan.value ? common_vendor.e({
        f: common_vendor.o(
          //@ts-ignore
          (...args) => common_vendor.unref(composables_useNavBar.navBack) && common_vendor.unref(composables_useNavBar.navBack)(...args)
        ),
        g: common_vendor.t(formatPlanDate(plan.value.date)),
        h: `${common_vendor.unref(utils_assets.STATIC_BASE_URL)}/static/guozai/action_01_bowl.png`,
        i: topInset.value,
        j: plan.value.nutrition
      }, plan.value.nutrition ? common_vendor.e({
        k: common_vendor.t(plan.value.nutrition.headline),
        l: common_vendor.f(nutritionChecklist.value, (item, k0, i0) => {
          return {
            a: common_vendor.t(item.done ? "✓" : "○"),
            b: common_vendor.t(item.label),
            c: item.key,
            d: !item.done ? 1 : ""
          };
        }),
        m: plan.value.nutrition.tip
      }, plan.value.nutrition.tip ? {
        n: common_vendor.t(plan.value.nutrition.goalTag || "均衡搭配"),
        o: common_vendor.t(plan.value.nutrition.tip)
      } : {}) : {}, {
        p: common_vendor.f(plan.value.meals, (meal, i, i0) => {
          return common_vendor.e({
            a: mealImage(meal),
            b: common_vendor.t(mealLabel(i)),
            c: common_vendor.t(MEAL_EN[i] || "MEAL"),
            d: common_vendor.t(mealLabel(i)),
            e: common_vendor.t(meal.cookingTime || "--"),
            f: common_vendor.t(meal.difficulty || "家常"),
            g: common_vendor.t(mealParts(meal.name).main),
            h: mealParts(meal.name).drink
          }, mealParts(meal.name).drink ? {
            i: common_vendor.t(mealParts(meal.name).drink)
          } : {}, {
            j: meal.description
          }, meal.description ? {
            k: common_vendor.t(meal.description)
          } : {}, {
            l: common_vendor.t(expanded[i] ? "收起做法" : "查看食材 / 做法"),
            m: common_vendor.o(($event) => toggle(i), i),
            n: expanded[i]
          }, expanded[i] ? {
            o: common_vendor.f(ingredientsFor(meal), (ing, k, i1) => {
              return {
                a: common_vendor.t(ing),
                b: k
              };
            }),
            p: common_vendor.f(stepsFor(meal), (step, k, i1) => {
              return {
                a: common_vendor.t(k + 1),
                b: common_vendor.t(step),
                c: k
              };
            })
          } : {}, {
            q: guidelineFor(i)
          }, guidelineFor(i) ? common_vendor.e({
            r: common_vendor.t(guidelineFor(i).title || "膳食指南建议"),
            s: common_vendor.t(guidelineFor(i).content),
            t: guidelineFor(i).source
          }, guidelineFor(i).source ? {
            v: common_vendor.t(guidelineFor(i).source)
          } : {}) : {}, {
            w: common_vendor.t(replacing[i] ? "替换中…" : "换一道"),
            x: common_vendor.o(($event) => replaceMeal(i), i),
            y: i,
            z: swapping[i] ? 1 : ""
          });
        }),
        q: common_vendor.t(generating.value ? "规划中…" : "换一整天"),
        r: common_vendor.o(reshuffle),
        s: common_vendor.o(talkToGuozai),
        t: common_vendor.t(sharing.value ? "生成中…" : "分享图"),
        v: common_vendor.o(share)
      }) : {
        w: common_vendor.p({
          color: "#FF6B5B"
        })
      }, {
        e: plan.value
      });
    };
  }
});
_sfc_defineComponent.__runtimeHooks = 6;
const MiniProgramPage = /* @__PURE__ */ common_vendor._export_sfc(_sfc_defineComponent, [["__scopeId", "data-v-aa2fe743"]]);
exports.MiniProgramPage = MiniProgramPage;
