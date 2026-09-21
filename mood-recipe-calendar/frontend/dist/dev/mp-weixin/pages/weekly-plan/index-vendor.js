"use strict";
const common_vendor = require("../../common/vendor.js");
const api_weeklyPlans = require("../../api/weeklyPlans.js");
const composables_useNavBar = require("../../composables/useNavBar.js");
const utils_assets = require("../../utils/assets.js");
const utils_toast = require("../../utils/toast.js");
const __unplugin_components_3 = () => "../../node-modules/@wot-ui/ui/components/wd-tour/wd-tour.js";
const __unplugin_components_2 = () => "../../node-modules/@wot-ui/ui/components/wd-tabs/wd-tabs.js";
const __unplugin_components_1 = () => "../../node-modules/@wot-ui/ui/components/wd-tab/wd-tab.js";
const __unplugin_components_0 = () => "../../node-modules/@wot-ui/ui/components/wd-navbar/wd-navbar.js";
if (!Array) {
  const _component_wd_navbar = __unplugin_components_0;
  const _component_wd_tab = __unplugin_components_1;
  const _component_wd_tabs = __unplugin_components_2;
  const _component_wd_tour = __unplugin_components_3;
  const _component_layout_default_uni = common_vendor.resolveComponent("layout-default-uni");
  (_component_wd_navbar + _component_wd_tab + _component_wd_tabs + _component_wd_tour + _component_layout_default_uni)();
}
const WEEKLY_PLAN_TOUR_KEY = "mrc-weekly-plan-tour-version";
const WEEKLY_PLAN_TOUR_VERSION = 1;
const _sfc_main = /* @__PURE__ */ common_vendor.defineComponent({
  __name: "index",
  setup(__props) {
    const router = common_vendor.useRouter();
    const people = common_vendor.ref(3);
    const dishesPerDay = common_vendor.ref(2);
    const cookingDays = common_vendor.ref([0, 1, 2, 3, 4, 5, 6]);
    const healthGoal = common_vendor.ref("BALANCED");
    const generating = common_vendor.ref(false);
    const loading = common_vendor.ref(true);
    const showComposer = common_vendor.ref(false);
    const activeTab = common_vendor.ref("current");
    const showTour = common_vendor.ref(false);
    const tourCurrent = common_vendor.ref(0);
    const currentPlan = common_vendor.ref();
    const history = common_vendor.ref([]);
    const tourSteps = [
      { element: "#weekly-plan-cover", content: "先认识锅仔的备餐本：它会记住口味、忌口和最近的状态，替你少想七次晚餐。", placement: "bottom" },
      { element: "#weekly-plan-tabs", content: "本周菜单和旧菜单分开放。想照着做看本周，想找收藏过的组合去备餐档案。", placement: "bottom" },
      { element: "#weekly-plan-current-action", content: "从这里打开本周菜单；不满意也能重新编排。锅仔会一步一步陪你开饭。", placement: "top" }
    ];
    const archivePlans = common_vendor.computed(() => history.value.filter((item) => {
      var _a;
      return item.id !== ((_a = currentPlan.value) == null ? void 0 : _a.id);
    }));
    const goals = [{ value: "BALANCED", label: "均衡吃" }, { value: "FITNESS", label: "练得好" }, { value: "LEAN", label: "轻一点" }];
    const dishCounts = [{ value: 1, label: "1 道", copy: "简单吃" }, { value: 2, label: "2 道", copy: "吃得完整" }, { value: 3, label: "3 道", copy: "吃得丰盛" }];
    const weekdays = ["周一", "周二", "周三", "周四", "周五", "周六", "周日"];
    function dishesOf(day) {
      var _a;
      return ((_a = day == null ? void 0 : day.dishes) == null ? void 0 : _a.length) ? day.dishes : day ? [{ name: day.dishName, ingredients: day.ingredients, steps: day.steps, fallbackImageUrl: day.fallbackImageUrl }] : [];
    }
    function dateLabel(value) {
      if (!value)
        return "刚刚安排";
      const date = new Date(value);
      return Number.isNaN(date.getTime()) ? "之前安排" : `${date.getMonth() + 1}月${date.getDate()}日`;
    }
    function openPlan(id) {
      router.push({ name: "weekly-plan-detail", query: id ? { id: String(id) } : {} });
    }
    function toggleCookingDay(index) {
      if (cookingDays.value.includes(index)) {
        if (cookingDays.value.length === 1) {
          utils_toast.toastError(null, "至少选一天，锅仔才知道何时为你开火");
          return;
        }
        cookingDays.value = cookingDays.value.filter((day) => day !== index);
        return;
      }
      cookingDays.value = [...cookingDays.value, index].sort((a, b) => a - b);
    }
    async function load() {
      loading.value = true;
      const [current, saved] = await Promise.allSettled([api_weeklyPlans.getCurrentPlan(), api_weeklyPlans.getWeeklyPlanHistory()]);
      currentPlan.value = current.status === "fulfilled" ? current.value : void 0;
      history.value = saved.status === "fulfilled" ? saved.value : [];
      loading.value = false;
      await startTour();
    }
    common_vendor.onShow(load);
    common_vendor.onHide(() => showTour.value = false);
    async function startTour(force = false) {
      if (loading.value || showTour.value || !force && Number(common_vendor.index.getStorageSync(WEEKLY_PLAN_TOUR_KEY)) >= WEEKLY_PLAN_TOUR_VERSION)
        return;
      activeTab.value = "current";
      showComposer.value = false;
      tourCurrent.value = 0;
      await common_vendor.nextTick$1();
      showTour.value = true;
    }
    function finishTour() {
      common_vendor.index.setStorageSync(WEEKLY_PLAN_TOUR_KEY, WEEKLY_PLAN_TOUR_VERSION);
      showTour.value = false;
    }
    async function generate() {
      if (generating.value)
        return;
      generating.value = true;
      try {
        const notify = await api_weeklyPlans.requestWeeklyPlanCompletionNotice();
        const plan = await api_weeklyPlans.generateWeeklyPlan({ people: people.value, days: cookingDays.value.length, cookingDays: cookingDays.value, healthGoal: healthGoal.value, sendNotification: notify, dishesPerDay: dishesPerDay.value });
        router.replace({ name: "weekly-plan-detail", query: { id: String(plan.id) } });
      } catch (error) {
        utils_toast.toastError(error, "锅仔暂时没排好这一周，请重试");
      } finally {
        generating.value = false;
      }
    }
    async function toggleFavorite(id) {
      try {
        const plan = await api_weeklyPlans.toggleWeeklyPlanFavorite(id);
        history.value = history.value.map((item) => item.id === id ? { ...item, favorite: plan.favorite } : item);
      } catch (error) {
        utils_toast.toastError(error, "收藏更新失败");
      }
    }
    return (_ctx, _cache) => {
      var _a, _b;
      return common_vendor.e({
        a: common_vendor.o(common_vendor.unref(composables_useNavBar.navBack)),
        b: common_vendor.p({
          title: "锅仔备餐小本",
          ["left-arrow"]: true,
          ["safe-area-inset-top"]: true,
          ["custom-style"]: "background-color: transparent !important;"
        }),
        c: `${common_vendor.unref(utils_assets.STATIC_BASE_URL)}/static/guozai/action_10_thinking.png`,
        d: common_vendor.o(($event) => startTour(true)),
        e: loading.value
      }, loading.value ? {} : common_vendor.e({
        f: !showComposer.value
      }, !showComposer.value ? common_vendor.e({
        g: currentPlan.value
      }, currentPlan.value ? {
        h: common_vendor.t(dateLabel(currentPlan.value.createdAt)),
        i: common_vendor.t(((_a = currentPlan.value.days[0]) == null ? void 0 : _a.dishName) || "这一周的晚餐"),
        j: common_vendor.t(currentPlan.value.days.length),
        k: common_vendor.t(dishesOf(currentPlan.value.days[0]).length),
        l: `${common_vendor.unref(utils_assets.STATIC_BASE_URL)}/static/guozai/action_11_cooking.png`,
        m: common_vendor.o(($event) => openPlan())
      } : {
        n: common_vendor.o(($event) => showComposer.value = true)
      }, {
        o: currentPlan.value
      }, currentPlan.value ? {
        p: common_vendor.o(($event) => showComposer.value = true)
      } : {}, {
        q: common_vendor.p({
          title: "本周菜单",
          name: "current"
        }),
        r: common_vendor.t(archivePlans.value.length),
        s: archivePlans.value.length === 0
      }, archivePlans.value.length === 0 ? {
        t: `${common_vendor.unref(utils_assets.STATIC_BASE_URL)}/static/guozai/action_08_peek.png`
      } : {
        v: common_vendor.f(archivePlans.value, (item, k0, i0) => {
          var _a2;
          return {
            a: common_vendor.t(dateLabel(item.createdAt)),
            b: common_vendor.t(item.days.reduce((total, day) => total + dishesOf(day).length, 0)),
            c: common_vendor.t(((_a2 = item.days[0]) == null ? void 0 : _a2.dishName) || "一周晚餐计划"),
            d: common_vendor.t(item.days.slice(1, 3).map((day) => day.dishName).join(" · ")),
            e: common_vendor.t(item.favorite ? "★" : "☆"),
            f: item.favorite ? "取消收藏这份计划" : "收藏这份计划",
            g: common_vendor.o(($event) => toggleFavorite(item.id), item.id),
            h: item.id,
            i: `查看${dateLabel(item.createdAt)}的计划`,
            j: common_vendor.o(($event) => openPlan(item.id), item.id)
          };
        }),
        w: `${common_vendor.unref(utils_assets.STATIC_BASE_URL)}/static/guozai/action_08_peek.png`
      }, {
        x: common_vendor.p({
          title: `备餐档案 ${archivePlans.value.length}`,
          name: "archive"
        }),
        y: common_vendor.o(($event) => activeTab.value = $event),
        z: common_vendor.p({
          animated: true,
          ["line-theme"]: "text",
          ["custom-class"]: "plan-tabs",
          ["custom-style"]: "--wot-tabs-nav-bg: transparent; --wot-tabs-nav-color: var(--mrc-text-sub); --wot-tabs-nav-color-active: var(--mrc-accent); --wot-tabs-nav-line-bg: var(--mrc-primary);",
          modelValue: activeTab.value
        })
      }) : {}, {
        A: showComposer.value
      }, showComposer.value ? {
        B: common_vendor.o(($event) => showComposer.value = false),
        C: common_vendor.o(($event) => people.value = Math.max(1, people.value - 1)),
        D: common_vendor.t(people.value),
        E: common_vendor.o(($event) => people.value = Math.min(8, people.value + 1)),
        F: common_vendor.f(dishCounts, (option, k0, i0) => {
          return {
            a: common_vendor.t(option.label),
            b: option.value,
            c: dishesPerDay.value === option.value ? 1 : "",
            d: dishesPerDay.value === option.value,
            e: common_vendor.o(($event) => dishesPerDay.value = option.value, option.value)
          };
        }),
        G: common_vendor.t((_b = dishCounts.find((option) => option.value === dishesPerDay.value)) == null ? void 0 : _b.copy),
        H: common_vendor.t(people.value >= 3 && dishesPerDay.value === 1 ? "三人建议选 2 道，吃得更完整。" : "锅仔会按人数搭配主菜和配菜。"),
        I: common_vendor.f(weekdays, (day, index, i0) => {
          return {
            a: common_vendor.t(day),
            b: day,
            c: cookingDays.value.includes(index) ? 1 : "",
            d: cookingDays.value.includes(index),
            e: `${cookingDays.value.includes(index) ? "取消" : "选择"}${day}晚餐`,
            f: common_vendor.o(($event) => toggleCookingDay(index), day)
          };
        }),
        J: common_vendor.f(goals, (goal, k0, i0) => {
          return {
            a: common_vendor.t(goal.label),
            b: goal.value,
            c: healthGoal.value === goal.value ? 1 : "",
            d: healthGoal.value === goal.value,
            e: common_vendor.o(($event) => healthGoal.value = goal.value, goal.value)
          };
        }),
        K: common_vendor.t(cookingDays.value.length),
        L: common_vendor.t(dishesPerDay.value),
        M: common_vendor.t(generating.value ? "锅仔正在排菜单…" : "生成这一册晚餐单"),
        N: generating.value || !cookingDays.value.length,
        O: common_vendor.o(generate)
      } : {}), {
        P: common_vendor.o(finishTour),
        Q: common_vendor.o(finishTour),
        R: common_vendor.o(($event) => showTour.value = $event),
        S: common_vendor.o(($event) => tourCurrent.value = $event),
        T: common_vendor.p({
          steps: tourSteps,
          padding: 10,
          ["border-radius"]: 18,
          ["z-index"]: 1200,
          ["custom-nav"]: true,
          ["missing-strategy"]: "skip",
          ["mask-color"]: "rgba(34, 20, 14, 0.68)",
          ["prev-text"]: "上一步",
          ["next-text"]: "下一步",
          ["skip-text"]: "不再提示",
          ["finish-text"]: "开始安排",
          ["custom-style"]: "--wot-tour-popover-bg: var(--mrc-surface); --wot-tour-info-color: var(--mrc-text); --wot-tour-popover-radius: 16px; --wot-tour-popover-padding: 18px;",
          modelValue: showTour.value,
          current: tourCurrent.value
        })
      });
    };
  }
});
const MiniProgramPage = /* @__PURE__ */ common_vendor._export_sfc(_sfc_main, [["__scopeId", "data-v-6e4e96f2"]]);
exports.MiniProgramPage = MiniProgramPage;
