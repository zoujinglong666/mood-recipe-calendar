"use strict";
const common_vendor = require("../../common/vendor.js");
const api_cookingAgent = require("../../api/cookingAgent.js");
const composables_useNavBar = require("../../composables/useNavBar.js");
const utils_assets = require("../../utils/assets.js");
const utils_cookingDraft = require("../../utils/cookingDraft.js");
const utils_toast = require("../../utils/toast.js");
const __unplugin_components_1 = () => "../../node-modules/@wot-ui/ui/components/wd-popup/wd-popup.js";
const __unplugin_components_0 = () => "../../node-modules/@wot-ui/ui/components/wd-navbar/wd-navbar.js";
if (!Array) {
  const _component_wd_navbar = __unplugin_components_0;
  const _component_wd_popup = __unplugin_components_1;
  const _component_layout_default_uni = common_vendor.resolveComponent("layout-default-uni");
  (_component_wd_navbar + _component_wd_popup + _component_layout_default_uni)();
}
if (!Math) {
  Icon();
}
const Icon = () => "../../components/common/Icon.js";
const _sfc_main = /* @__PURE__ */ common_vendor.defineComponent({
  __name: "index",
  setup(__props) {
    const router = common_vendor.useRouter();
    const draft = common_vendor.ref(utils_cookingDraft.loadCookingDraft());
    const stepIndex = common_vendor.ref(0);
    const ingredientsOpen = common_vendor.ref(false);
    const remaining = common_vendor.ref(300);
    const deadline = common_vendor.ref(0);
    const running = common_vendor.ref(false);
    const guide = common_vendor.ref(null);
    const guideLoading = common_vendor.ref(false);
    const coachOpen = common_vendor.ref(false);
    const asking = common_vendor.ref(false);
    const question = common_vendor.ref("");
    const messages = common_vendor.ref([]);
    const personalized = common_vendor.ref(true);
    const sourcesOpen = common_vendor.ref(false);
    const memoryOpen = common_vendor.ref(false);
    const sessionId = `${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 9)}`;
    let ticker;
    function parseList(value) {
      try {
        const parsed = JSON.parse(value || "[]");
        return Array.isArray(parsed) ? parsed.map(String).filter(Boolean) : [];
      } catch {
        return [];
      }
    }
    const recipe = common_vendor.computed(() => {
      var _a;
      return (_a = draft.value) == null ? void 0 : _a.recipe;
    });
    const steps = common_vendor.computed(() => {
      var _a;
      return parseList((_a = recipe.value) == null ? void 0 : _a.steps);
    });
    const ingredients = common_vendor.computed(() => {
      var _a;
      return parseList((_a = recipe.value) == null ? void 0 : _a.ingredients);
    });
    const currentStep = common_vendor.computed(() => steps.value[stepIndex.value] || "");
    const recipeKey = common_vendor.computed(() => {
      var _a, _b;
      return String(((_a = recipe.value) == null ? void 0 : _a.id) || ((_b = recipe.value) == null ? void 0 : _b.name) || "");
    });
    const progressPercent = common_vendor.computed(() => steps.value.length ? Math.round((stepIndex.value + 1) / steps.value.length * 100) : 0);
    const timerText = common_vendor.computed(() => `${String(Math.floor(remaining.value / 60)).padStart(2, "0")}:${String(remaining.value % 60).padStart(2, "0")}`);
    const currentGuide = common_vendor.computed(() => {
      var _a;
      return (_a = guide.value) == null ? void 0 : _a.guideSteps.find((item) => item.index === stepIndex.value + 1);
    });
    const suggestions = common_vendor.computed(() => {
      var _a, _b;
      return ((_b = (_a = guide.value) == null ? void 0 : _a.suggestions) == null ? void 0 : _b.length) ? guide.value.suggestions : ["怎样算熟？", "火太大怎么补救？", "没有这个食材怎么换？"];
    });
    async function loadGuide() {
      var _a;
      if (!((_a = recipe.value) == null ? void 0 : _a.id) || guideLoading.value)
        return;
      guideLoading.value = true;
      try {
        guide.value = await api_cookingAgent.cookingAgentTurn({
          recipeId: recipe.value.id,
          currentStep: stepIndex.value,
          sessionId,
          action: "GUIDE",
          personalized: personalized.value
        });
      } catch {
        guide.value = null;
      } finally {
        guideLoading.value = false;
      }
    }
    async function ask(text = question.value) {
      var _a, _b;
      const content = text.trim();
      if (!content || asking.value || !((_a = recipe.value) == null ? void 0 : _a.id))
        return;
      const history = messages.value.slice(-8);
      messages.value.push({ role: "user", content });
      question.value = "";
      asking.value = true;
      try {
        const response = await api_cookingAgent.cookingAgentTurn({
          recipeId: recipe.value.id,
          currentStep: stepIndex.value,
          sessionId,
          message: content,
          action: "ASK",
          personalized: personalized.value,
          history
        });
        guide.value = { ...response, guideSteps: response.guideSteps.length ? response.guideSteps : ((_b = guide.value) == null ? void 0 : _b.guideSteps) || [] };
        messages.value.push({ role: "assistant", content: response.reply });
      } catch {
        messages.value.push({ role: "assistant", content: "刚才连接断了一下，原步骤和计时不受影响，可以再问一次。" });
      } finally {
        asking.value = false;
      }
    }
    async function feedback(eventType) {
      var _a;
      if (!((_a = recipe.value) == null ? void 0 : _a.id))
        return;
      try {
        await api_cookingAgent.sendCookingFeedback({ recipeId: recipe.value.id, stepIndex: stepIndex.value, stepType: currentStep.value.slice(0, 48), eventType });
        utils_toast.toast(eventType === "COMPLETED" ? "记下啦，下次会更懂你的节奏" : "记下啦，下次这类步骤会讲得更细");
      } catch {
        utils_toast.toast("暂时没记上，不影响继续做菜");
      }
    }
    async function clearLearning() {
      const result = await common_vendor.index.showModal({ title: "清除做菜学习？", content: "只清除做菜熟练度反馈，不会删除菜谱和做饭记录。", confirmText: "清除" });
      if (!result.confirm)
        return;
      await api_cookingAgent.clearCookingLearning();
      utils_toast.toast("做菜学习已清除");
    }
    function copySource(url) {
      common_vendor.index.setClipboardData({ data: url, success: () => utils_toast.toast("来源链接已复制") });
    }
    function defaultSeconds() {
      const match = currentStep.value.match(/(\d+)\s*分钟/);
      return match ? Math.max(60, Math.min(Number(match[1]) * 60, 3600)) : 300;
    }
    function persist() {
      if (!recipeKey.value)
        return;
      common_vendor.index.setStorageSync(utils_cookingDraft.COOKING_PROGRESS_KEY, {
        recipeKey: recipeKey.value,
        stepIndex: stepIndex.value,
        remaining: remaining.value,
        deadline: deadline.value,
        running: running.value
      });
    }
    function syncRemaining() {
      if (!running.value || !deadline.value)
        return;
      remaining.value = Math.max(0, Math.ceil((deadline.value - Date.now()) / 1e3));
      if (remaining.value === 0) {
        running.value = false;
        deadline.value = 0;
        stopTicker();
        vibrate();
        utils_toast.toast("这一段计时完成啦");
      }
      persist();
    }
    function startTicker() {
      stopTicker();
      ticker = setInterval(syncRemaining, 500);
    }
    function stopTicker() {
      if (ticker)
        clearInterval(ticker);
      ticker = void 0;
    }
    function toggleTimer() {
      if (!remaining.value)
        remaining.value = defaultSeconds();
      if (running.value) {
        syncRemaining();
        running.value = false;
        deadline.value = 0;
        stopTicker();
      } else {
        running.value = true;
        deadline.value = Date.now() + remaining.value * 1e3;
        startTicker();
      }
      persist();
    }
    function resetTimer() {
      stopTicker();
      running.value = false;
      deadline.value = 0;
      remaining.value = defaultSeconds();
      persist();
    }
    function vibrate() {
      try {
        common_vendor.index.vibrateShort({ type: "light" });
      } catch {
      }
    }
    function move(offset) {
      const next = stepIndex.value + offset;
      if (next < 0 || next >= steps.value.length)
        return;
      stepIndex.value = next;
      resetTimer();
      vibrate();
    }
    async function restart() {
      const result = await common_vendor.index.showModal({ title: "从第一步重新开始？", content: "当前步骤和计时会重置。", confirmText: "重新开始" });
      if (!result.confirm)
        return;
      stepIndex.value = 0;
      resetTimer();
    }
    async function finish() {
      if (!recipe.value || !draft.value)
        return;
      stopTicker();
      if (recipe.value.id)
        await api_cookingAgent.sendCookingFeedback({ recipeId: recipe.value.id, eventType: "COMPLETED" }).catch(() => void 0);
      common_vendor.index.removeStorageSync(utils_cookingDraft.COOKING_PROGRESS_KEY);
      utils_cookingDraft.saveRecordDraft(recipe.value, draft.value.mood);
      router.pushTab({ name: "record" });
    }
    common_vendor.onLoad(() => {
      if (!recipe.value || !steps.value.length)
        return;
      const saved = common_vendor.index.getStorageSync(utils_cookingDraft.COOKING_PROGRESS_KEY);
      if ((saved == null ? void 0 : saved.recipeKey) === recipeKey.value) {
        stepIndex.value = Math.min(Math.max(saved.stepIndex || 0, 0), steps.value.length - 1);
        remaining.value = Math.max(0, saved.remaining || defaultSeconds());
        deadline.value = saved.deadline || 0;
        running.value = Boolean(saved.running && saved.deadline);
        syncRemaining();
      } else {
        resetTimer();
      }
      void loadGuide();
    });
    common_vendor.onShow(() => {
      if (running.value) {
        syncRemaining();
        if (running.value)
          startTicker();
      }
    });
    common_vendor.onHide(() => {
      syncRemaining();
      stopTicker();
    });
    common_vendor.onUnload(() => {
      syncRemaining();
      stopTicker();
    });
    return (_ctx, _cache) => {
      var _a, _b, _c, _d, _e, _f, _g, _h, _i, _j, _k, _l;
      return common_vendor.e({
        a: common_vendor.o(common_vendor.unref(composables_useNavBar.navBack)),
        b: common_vendor.p({
          title: "跟锅仔做菜",
          ["left-arrow"]: true,
          ["safe-area-inset-top"]: true,
          ["custom-style"]: "background-color: transparent !important;"
        }),
        c: !recipe.value || !steps.value.length
      }, !recipe.value || !steps.value.length ? {
        d: `${common_vendor.unref(utils_assets.STATIC_BASE_URL)}/static/guozai/action_07_empty.png`,
        e: common_vendor.o(
          //@ts-ignore
          (...args) => common_vendor.unref(composables_useNavBar.navBack) && common_vendor.unref(composables_useNavBar.navBack)(...args)
        )
      } : common_vendor.e({
        f: common_vendor.t(recipe.value.name),
        g: common_vendor.t(recipe.value.cookingTime || "--"),
        h: common_vendor.t(recipe.value.difficulty || "家常难度"),
        i: `${common_vendor.unref(utils_assets.STATIC_BASE_URL)}/static/guozai/action_16_chopsticks.png`,
        j: `${progressPercent.value}%`,
        k: common_vendor.t(stepIndex.value + 1),
        l: common_vendor.t(steps.value.length),
        m: common_vendor.o(restart),
        n: common_vendor.t(String(stepIndex.value + 1).padStart(2, "0")),
        o: common_vendor.t(currentStep.value),
        p: `${common_vendor.unref(utils_assets.STATIC_BASE_URL)}/static/guozai/action_10_thinking.png`,
        q: guideLoading.value
      }, guideLoading.value ? {} : currentGuide.value ? common_vendor.e({
        s: common_vendor.t(((_a = guide.value) == null ? void 0 : _a.teachingLevel) === "BEGINNER" ? "新手细讲" : ((_b = guide.value) == null ? void 0 : _b.teachingLevel) === "COMPACT" ? "熟练模式" : "跟做模式"),
        t: common_vendor.t(currentGuide.value.heat),
        v: common_vendor.t(currentGuide.value.duration),
        w: common_vendor.t(currentGuide.value.successSigns),
        x: common_vendor.t(currentGuide.value.rescue),
        y: (_c = guide.value) == null ? void 0 : _c.degraded
      }, ((_d = guide.value) == null ? void 0 : _d.degraded) ? {
        z: common_vendor.t(guide.value.degradeReason === "NO_EVIDENCE" ? "知识库暂无匹配依据" : "智能教学暂时降级")
      } : {}) : {}, {
        r: currentGuide.value,
        A: `${common_vendor.unref(utils_assets.STATIC_BASE_URL)}/static/guozai/action_10_thinking.png`,
        B: common_vendor.o(($event) => coachOpen.value = true),
        C: common_vendor.p({
          name: "clock",
          size: 38,
          color: "#EF5A3C"
        }),
        D: common_vendor.t(timerText.value),
        E: common_vendor.t(running.value ? "暂停" : remaining.value ? "开始" : "再次计时"),
        F: running.value ? "暂停计时" : "开始计时",
        G: common_vendor.o(toggleTimer),
        H: common_vendor.o(resetTimer),
        I: common_vendor.t(ingredients.value.length),
        J: ingredientsOpen.value ? 1 : "",
        K: ingredientsOpen.value,
        L: common_vendor.o(($event) => ingredientsOpen.value = !ingredientsOpen.value),
        M: ingredientsOpen.value
      }, ingredientsOpen.value ? {
        N: common_vendor.f(ingredients.value, (item, index, i0) => {
          return {
            a: common_vendor.t(index + 1),
            b: common_vendor.t(item),
            c: `${item}-${index}`
          };
        })
      } : {}, {
        O: stepIndex.value === 0 ? 1 : "",
        P: common_vendor.o(($event) => move(-1)),
        Q: stepIndex.value < steps.value.length - 1
      }, stepIndex.value < steps.value.length - 1 ? {
        R: common_vendor.o(($event) => move(1))
      } : {
        S: common_vendor.o(finish)
      }, {
        T: common_vendor.t(stepIndex.value + 1),
        U: common_vendor.o(($event) => coachOpen.value = false),
        V: common_vendor.t(currentStep.value),
        W: !messages.value.length
      }, !messages.value.length ? {
        X: `${common_vendor.unref(utils_assets.STATIC_BASE_URL)}/static/guozai/action_16_chopsticks.png`
      } : {}, {
        Y: common_vendor.f(messages.value, (message, index, i0) => {
          return {
            a: common_vendor.t(message.content),
            b: `${index}-${message.role}`,
            c: common_vendor.n(`chat-row--${message.role}`)
          };
        }),
        Z: asking.value
      }, asking.value ? {} : {}, {
        aa: (_f = (_e = guide.value) == null ? void 0 : _e.sources) == null ? void 0 : _f.length
      }, ((_h = (_g = guide.value) == null ? void 0 : _g.sources) == null ? void 0 : _h.length) ? common_vendor.e({
        ab: common_vendor.t(guide.value.sources.length),
        ac: common_vendor.t(sourcesOpen.value ? "收起" : "查看"),
        ad: sourcesOpen.value,
        ae: common_vendor.o(($event) => sourcesOpen.value = !sourcesOpen.value),
        af: sourcesOpen.value
      }, sourcesOpen.value ? {
        ag: common_vendor.f(guide.value.sources, (source, k0, i0) => {
          return {
            a: common_vendor.t(source.title),
            b: common_vendor.t(source.sourceName),
            c: common_vendor.t(source.version),
            d: source.id,
            e: `复制来源：${source.title}`,
            f: common_vendor.o(($event) => copySource(source.sourceUrl), source.id)
          };
        })
      } : {}) : guide.value ? {} : {}, {
        ah: guide.value,
        ai: common_vendor.t(personalized.value ? "已开启" : "已关闭"),
        aj: personalized.value ? 1 : "",
        ak: personalized.value,
        al: common_vendor.o(($event) => {
          personalized.value = !personalized.value;
          loadGuide();
        }),
        am: (_j = (_i = guide.value) == null ? void 0 : _i.memoryUsed) == null ? void 0 : _j.length
      }, ((_l = (_k = guide.value) == null ? void 0 : _k.memoryUsed) == null ? void 0 : _l.length) ? {
        an: common_vendor.t(guide.value.memoryUsed.length),
        ao: common_vendor.t(memoryOpen.value ? "收起" : "为什么"),
        ap: memoryOpen.value,
        aq: common_vendor.o(($event) => memoryOpen.value = !memoryOpen.value),
        ar: common_vendor.f(memoryOpen.value ? guide.value.memoryUsed : [], (item, k0, i0) => {
          return {
            a: common_vendor.t(item),
            b: item
          };
        })
      } : {}, {
        as: common_vendor.o(($event) => feedback("TOO_HARD")),
        at: common_vendor.o(($event) => feedback("NOT_COMPLETED")),
        av: common_vendor.o(clearLearning),
        aw: common_vendor.f(suggestions.value, (item, k0, i0) => {
          return {
            a: common_vendor.t(item),
            b: item,
            c: common_vendor.o(($event) => ask(item), item)
          };
        }),
        ax: asking.value,
        ay: common_vendor.o(($event) => ask()),
        az: question.value,
        aA: common_vendor.o(($event) => question.value = $event.detail.value),
        aB: asking.value || !question.value.trim(),
        aC: common_vendor.o(($event) => ask()),
        aD: common_vendor.o(($event) => coachOpen.value = $event),
        aE: common_vendor.p({
          position: "bottom",
          ["close-on-click-modal"]: !asking.value,
          ["custom-style"]: "border-radius: 40rpx 40rpx 0 0; overflow: hidden; background: var(--mrc-bg);",
          modelValue: coachOpen.value
        })
      }));
    };
  }
});
const MiniProgramPage = /* @__PURE__ */ common_vendor._export_sfc(_sfc_main, [["__scopeId", "data-v-de58d624"]]);
exports.MiniProgramPage = MiniProgramPage;
