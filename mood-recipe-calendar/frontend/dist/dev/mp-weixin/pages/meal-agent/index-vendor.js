"use strict";
const common_vendor = require("../../common/vendor.js");
const api_feedback = require("../../api/feedback.js");
const api_preferences = require("../../api/preferences.js");
const api_weeklyPlans = require("../../api/weeklyPlans.js");
const composables_useNavBar = require("../../composables/useNavBar.js");
const utils_assets = require("../../utils/assets.js");
const utils_toast = require("../../utils/toast.js");
const __unplugin_components_2 = () => "../../node-modules/@wot-ui/ui/components/wd-radio-group/wd-radio-group.js";
const __unplugin_components_1 = () => "../../node-modules/@wot-ui/ui/components/wd-radio/wd-radio.js";
const __unplugin_components_0 = () => "../../node-modules/@wot-ui/ui/components/wd-navbar/wd-navbar.js";
if (!Array) {
  const _component_wd_navbar = __unplugin_components_0;
  const _component_wd_radio = __unplugin_components_1;
  const _component_wd_radio_group = __unplugin_components_2;
  const _component_layout_default_uni = common_vendor.resolveComponent("layout-default-uni");
  (_component_wd_navbar + _component_wd_radio + _component_wd_radio_group + _component_layout_default_uni)();
}
const _sfc_main = /* @__PURE__ */ common_vendor.defineComponent({
  __name: "index",
  setup(__props) {
    const router = common_vendor.useRouter();
    const memory = common_vendor.ref();
    const currentPlan = common_vendor.ref();
    const loading = common_vendor.ref(true);
    const generating = common_vendor.ref(false);
    const people = common_vendor.ref(3);
    const dishesPerDay = common_vendor.ref(2);
    const cookingDays = common_vendor.ref([0, 1, 2, 3, 4, 5, 6]);
    const healthGoal = common_vendor.ref("BALANCED");
    const budget = common_vendor.ref("DAILY");
    const activeStep = common_vendor.ref(-1);
    const completed = common_vendor.ref(false);
    const generatedPlanId = common_vendor.ref();
    const rating = common_vendor.ref("");
    const hasElder = common_vendor.ref(false);
    const hasChild = common_vendor.ref(false);
    const spiceLevel = common_vendor.ref("微辣");
    const sessionCuisine = common_vendor.ref("");
    const composerText = common_vendor.ref("");
    const messages = common_vendor.ref([]);
    const agentState = common_vendor.ref({});
    const agentTurn = common_vendor.ref();
    const agentBusy = common_vendor.ref(false);
    const householdSelection = common_vendor.ref([]);
    let messageId = 0;
    let progressTimer;
    const agentSteps = [
      { title: "读取锅仔记忆", copy: "口味、忌口和最近做过的菜" },
      { title: "检查家庭情况", copy: "老人、小孩和吃辣程度" },
      { title: "检查本周安排", copy: "做饭日期和每天菜数" },
      { title: "搭配主菜与配菜", copy: "避开拒绝过的菜，减少重复" },
      { title: "合并买菜清单", copy: "复用食材，整理成一张清单" }
    ];
    const memoryTags = common_vendor.computed(() => {
      var _a;
      const value = memory.value;
      if (!value)
        return ["正在认识你"];
      const tags = [];
      const cuisines = ((_a = value.explicit.favoriteCuisines) == null ? void 0 : _a.split(/[,，、]/).filter(Boolean)) || [];
      tags.push(...cuisines.slice(0, 2));
      if (value.explicit.eatCilantro === false)
        tags.push("不吃香菜");
      if (value.explicit.eatScallion === false)
        tags.push("不吃葱");
      if (value.behavior.topDish)
        tags.push(`常做${value.behavior.topDish}`);
      return tags.length ? tags.slice(0, 4) : ["口味还在慢慢积累"];
    });
    const agentGreeting = common_vendor.computed(() => {
      var _a;
      const value = memory.value;
      if (!value)
        return "我会带上你已经留下的口味和忌口，替你把这一周安排好。";
      const cuisine = (_a = value.explicit.favoriteCuisines) == null ? void 0 : _a.split(/[,，、]/).filter(Boolean)[0];
      const avoid = value.explicit.eatCilantro === false ? "不放香菜" : value.explicit.eatScallion === false ? "不放葱" : "避开你的忌口";
      return cuisine ? `我记得你喜欢${cuisine}、${avoid}。这周的变化，再告诉我一点就好。` : `我会${avoid}，也会参考你最近做过的菜。这周想怎么吃？`;
    });
    const conversationNotes = common_vendor.computed(() => {
      const state = agentState.value;
      return [
        state.hasElder ? "家有老人，菜品软烂少盐" : "",
        state.hasChild ? "家有小孩，少刺少骨、口味温和" : "",
        state.spiceLevel ? `吃辣程度：${state.spiceLevel}` : "",
        state.favoriteCuisine ? `偏爱${state.favoriteCuisine}` : "",
        state.mealContext || ""
      ].filter(Boolean).join("；");
    });
    common_vendor.onShow(load);
    common_vendor.onUnmounted(stopProgress);
    async function load() {
      var _a;
      loading.value = true;
      const [memoryResult, planResult] = await Promise.allSettled([api_preferences.fetchFoodMemory(), api_weeklyPlans.getCurrentPlan()]);
      memory.value = memoryResult.status === "fulfilled" ? memoryResult.value : void 0;
      currentPlan.value = planResult.status === "fulfilled" ? planResult.value : void 0;
      if ((_a = memory.value) == null ? void 0 : _a.explicit.healthGoal)
        healthGoal.value = memory.value.explicit.healthGoal;
      if (!messages.value.length) {
        addAgent(agentGreeting.value, memoryTags.value);
        await runAgent("");
      }
      loading.value = false;
    }
    function addAgent(text, tags) {
      messages.value.push({ id: ++messageId, role: "agent", text, tags });
    }
    async function scrollToLatest() {
      await common_vendor.nextTick$1();
      common_vendor.index.pageScrollTo({ scrollTop: 999999, duration: 220 });
    }
    async function runAgent(message, echo = false, echoLabel = message) {
      if (agentBusy.value)
        return;
      if (echo && message)
        messages.value.push({ id: ++messageId, role: "user", text: echoLabel });
      agentBusy.value = true;
      await scrollToLatest();
      try {
        const turn = await api_weeklyPlans.runMealAgentTurn(message, agentState.value);
        agentTurn.value = turn;
        agentState.value = turn.state;
        householdSelection.value = turn.action === "ASK_HOUSEHOLD" ? [turn.state.hasElder ? "elder" : "", turn.state.hasChild ? "child" : ""].filter(Boolean) : [];
        people.value = turn.state.people || people.value;
        cookingDays.value = turn.state.cookingDays || [];
        dishesPerDay.value = turn.state.dishesPerDay || dishesPerDay.value;
        healthGoal.value = turn.state.healthGoal || healthGoal.value;
        budget.value = turn.state.budget || budget.value;
        hasElder.value = turn.state.hasElder ?? hasElder.value;
        hasChild.value = turn.state.hasChild ?? hasChild.value;
        spiceLevel.value = turn.state.spiceLevel || spiceLevel.value;
        sessionCuisine.value = turn.state.favoriteCuisine || sessionCuisine.value;
        addAgent(turn.reply);
      } catch (error) {
        utils_toast.toastError(error, "锅仔刚刚走神了，请再说一次");
      } finally {
        agentBusy.value = false;
        await scrollToLatest();
      }
    }
    function householdValue(value) {
      if (value === "elder=yes" || value.includes("elder"))
        return "elder";
      if (value === "child=yes" || value.includes("child"))
        return "child";
      return "none";
    }
    function toggleHousehold(value) {
      const selected = householdValue(value);
      if (selected === "none") {
        householdSelection.value = ["none"];
        return;
      }
      const current = householdSelection.value.filter((item) => item !== "none");
      householdSelection.value = current.includes(selected) ? current.filter((item) => item !== selected) : [...current, selected];
    }
    async function confirmHousehold() {
      if (!householdSelection.value.length)
        return;
      const none = householdSelection.value.includes("none");
      const value = none ? "household=none" : `household=${householdSelection.value.join(",")}`;
      const label = none ? "都是成人" : householdSelection.value.map((item) => item === "elder" ? "有老人" : "有小孩").join("、");
      await runAgent(value, true, label);
    }
    async function submitComposer() {
      const text = composerText.value.trim();
      if (!text)
        return;
      composerText.value = "";
      await runAgent(text, true);
    }
    function startProgress() {
      completed.value = false;
      activeStep.value = 0;
      progressTimer = setInterval(() => {
        if (activeStep.value < agentSteps.length - 1)
          activeStep.value += 1;
      }, 900);
    }
    function stopProgress() {
      if (progressTimer)
        clearInterval(progressTimer);
      progressTimer = void 0;
    }
    async function generate() {
      if (generating.value)
        return;
      generating.value = true;
      startProgress();
      try {
        const notify = await api_weeklyPlans.requestWeeklyPlanCompletionNotice();
        const plan = await api_weeklyPlans.generateWeeklyPlan({
          people: people.value,
          days: cookingDays.value.length,
          cookingDays: cookingDays.value,
          healthGoal: healthGoal.value,
          dishesPerDay: dishesPerDay.value,
          budget: budget.value,
          conversationNotes: conversationNotes.value,
          sendNotification: notify
        });
        stopProgress();
        activeStep.value = agentSteps.length;
        completed.value = true;
        generatedPlanId.value = plan.id;
      } catch (error) {
        stopProgress();
        activeStep.value = -1;
        utils_toast.toastError(error, "锅仔这次没排好，请再试一次");
      } finally {
        generating.value = false;
      }
    }
    async function rateConversation(value) {
      if (rating.value)
        return;
      rating.value = value;
      try {
        await api_feedback.submitFeedback({ category: "体验问题", content: `锅仔管饭对话评分：${value}` });
      } catch {
      }
    }
    function onRatingChange(event) {
      void rateConversation(String(event.value));
    }
    function openGeneratedPlan() {
      if (generatedPlanId.value)
        router.replace({ name: "weekly-plan-detail", query: { id: String(generatedPlanId.value) } });
    }
    function openCurrentPlan() {
      if (currentPlan.value)
        router.push({ name: "weekly-plan-detail", query: { id: String(currentPlan.value.id) } });
    }
    return (_ctx, _cache) => {
      return common_vendor.e({
        a: common_vendor.o(common_vendor.unref(composables_useNavBar.navBack)),
        b: common_vendor.p({
          title: "锅仔管饭",
          ["left-arrow"]: true,
          ["safe-area-inset-top"]: true,
          ["custom-style"]: "background-color: transparent !important;"
        }),
        c: `${common_vendor.unref(utils_assets.STATIC_BASE_URL)}/static/guozai/action_06_glasses.png`,
        d: loading.value
      }, loading.value ? {} : common_vendor.e({
        e: currentPlan.value
      }, currentPlan.value ? {
        f: common_vendor.t(currentPlan.value.days.length),
        g: common_vendor.o(openCurrentPlan)
      } : {}, {
        h: generating.value || completed.value
      }, generating.value || completed.value ? common_vendor.e({
        i: common_vendor.t(completed.value ? "这一周已经排好" : "锅仔正在调用工具"),
        j: common_vendor.t(completed.value ? "完成" : `${Math.min(activeStep.value + 1, agentSteps.length)}/${agentSteps.length}`),
        k: common_vendor.f(agentSteps, (step, index, i0) => {
          return common_vendor.e({
            a: completed.value || index < activeStep.value
          }, completed.value || index < activeStep.value ? {} : index === activeStep.value ? {} : {
            c: common_vendor.t(index + 1)
          }, {
            b: index === activeStep.value,
            d: common_vendor.t(step.title),
            e: common_vendor.t(step.copy),
            f: step.title,
            g: completed.value || index < activeStep.value ? 1 : "",
            h: !completed.value && index === activeStep.value ? 1 : ""
          });
        }),
        l: completed.value
      }, completed.value ? {
        m: common_vendor.p({
          value: "满意"
        }),
        n: common_vendor.p({
          value: "一般"
        }),
        o: common_vendor.p({
          value: "不满意"
        }),
        p: common_vendor.o(onRatingChange),
        q: common_vendor.p({
          ["custom-class"]: "rating-selector",
          type: "button",
          direction: "horizontal",
          ["model-value"]: rating.value
        }),
        r: common_vendor.o(openGeneratedPlan)
      } : {}) : {}, {
        s: generating.value || completed.value
      }, generating.value || completed.value ? {
        t: agentBusy.value,
        v: common_vendor.o(submitComposer),
        w: composerText.value,
        x: common_vendor.o(($event) => composerText.value = $event.detail.value),
        y: common_vendor.t(agentBusy.value ? "思考中" : "发送"),
        z: agentBusy.value,
        A: agentBusy.value ? "锅仔正在思考" : "发送",
        B: common_vendor.o(submitComposer)
      } : common_vendor.e({
        C: common_vendor.f(messages.value, (message, k0, i0) => {
          var _a, _b;
          return common_vendor.e({
            a: message.role === "agent"
          }, message.role === "agent" ? {
            b: `${common_vendor.unref(utils_assets.STATIC_BASE_URL)}/static/guozai/action_08_peek.png`
          } : {}, {
            c: common_vendor.t(message.text),
            d: (_a = message.tags) == null ? void 0 : _a.length
          }, ((_b = message.tags) == null ? void 0 : _b.length) ? {
            e: common_vendor.f(message.tags, (tag, k1, i1) => {
              return {
                a: common_vendor.t(tag),
                b: tag
              };
            })
          } : {}, {
            f: message.id,
            g: common_vendor.n(`chat-row--${message.role}`)
          });
        }),
        D: agentBusy.value
      }, agentBusy.value ? {
        E: `${common_vendor.unref(utils_assets.STATIC_BASE_URL)}/static/guozai/action_08_peek.png`
      } : {}, {
        F: agentTurn.value && !agentBusy.value
      }, agentTurn.value && !agentBusy.value ? common_vendor.e({
        G: common_vendor.t(agentTurn.value.card.title),
        H: common_vendor.t(agentTurn.value.card.description),
        I: agentTurn.value.card.type === "CUISINE"
      }, agentTurn.value.card.type === "CUISINE" ? {
        J: `${common_vendor.unref(utils_assets.STATIC_BASE_URL)}/static/guozai/action_06_glasses.png`
      } : {}, {
        K: agentTurn.value.action === "ASK_HOUSEHOLD"
      }, agentTurn.value.action === "ASK_HOUSEHOLD" ? {
        L: common_vendor.f(agentTurn.value.card.options, (option, k0, i0) => {
          return {
            a: common_vendor.t(householdSelection.value.includes(householdValue(option.value)) ? "✓" : ""),
            b: common_vendor.t(option.label),
            c: option.value,
            d: householdSelection.value.includes(householdValue(option.value)) ? 1 : "",
            e: householdSelection.value.includes(householdValue(option.value)),
            f: common_vendor.o(($event) => toggleHousehold(option.value), option.value)
          };
        }),
        M: agentBusy.value,
        N: !householdSelection.value.length || agentBusy.value,
        O: common_vendor.o(confirmHousehold)
      } : {
        P: common_vendor.f(agentTurn.value.card.options, (option, k0, i0) => {
          return {
            a: common_vendor.t(option.label),
            b: option.value,
            c: common_vendor.o(($event) => option.value === "generate" ? generate() : runAgent(option.value, true, option.label), option.value)
          };
        }),
        Q: agentBusy.value,
        R: agentTurn.value.card.type === "CUISINE" ? 1 : ""
      }, {
        S: agentTurn.value.card.type === "CUISINE" ? 1 : ""
      }) : {}, {
        T: agentBusy.value,
        U: common_vendor.o(submitComposer),
        V: composerText.value,
        W: common_vendor.o(($event) => composerText.value = $event.detail.value),
        X: common_vendor.t(agentBusy.value ? "思考中" : "发送"),
        Y: agentBusy.value,
        Z: agentBusy.value ? "锅仔正在思考" : "发送",
        aa: common_vendor.o(submitComposer),
        ab: common_vendor.o(($event) => common_vendor.unref(router).push({
          name: "weekly-plan"
        }))
      })));
    };
  }
});
const MiniProgramPage = /* @__PURE__ */ common_vendor._export_sfc(_sfc_main, [["__scopeId", "data-v-d8896d00"]]);
exports.MiniProgramPage = MiniProgramPage;
