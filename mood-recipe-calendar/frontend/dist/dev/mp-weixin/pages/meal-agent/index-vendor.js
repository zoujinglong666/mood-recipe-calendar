"use strict";
const common_vendor = require("../../common/vendor.js");
const api_preferences = require("../../api/preferences.js");
const api_weeklyPlans = require("../../api/weeklyPlans.js");
const composables_useNavBar = require("../../composables/useNavBar.js");
const utils_assets = require("../../utils/assets.js");
const utils_safeDecodePrompt = require("../../utils/safeDecodePrompt.js");
const utils_toast = require("../../utils/toast.js");
const stores_user = require("../../stores/user.js");
const utils_login = require("../../utils/login.js");
const utils_memberGate = require("../../utils/memberGate.js");
const api_share = require("../../api/share.js");
const __unplugin_components_0 = () => "../../node-modules/@wot-ui/ui/components/wd-navbar/wd-navbar.js";
if (!Array) {
  const _component_wd_navbar = __unplugin_components_0;
  const _component_layout_default_uni = common_vendor.resolveComponent("layout-default-uni");
  (_component_wd_navbar + _component_layout_default_uni)();
}
if (!Math) {
  (GuozaiChipGroup + GuozaiButton + Icon)();
}
const GuozaiChipGroup = () => "../../components/guozai/GuozaiChipGroup.js";
const GuozaiButton = () => "../../components/guozai/GuozaiButton.js";
const Icon = () => "../../components/common/Icon.js";
const _sfc_defineComponent = common_vendor.defineComponent({
  __name: "index",
  setup(__props) {
    const router = common_vendor.useRouter();
    const route = common_vendor.useRoute();
    const userStore = stores_user.useUserStore();
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
    const RATING_OPTIONS = [
      { value: "满意", label: "刚刚好" },
      { value: "一般", label: "还行" },
      { value: "不满意", label: "不太对" }
    ];
    const hasElder = common_vendor.ref(false);
    const hasChild = common_vendor.ref(false);
    const spiceLevel = common_vendor.ref("微辣");
    const sessionCuisine = common_vendor.ref("");
    const composerText = common_vendor.ref("");
    const messages = common_vendor.ref([]);
    const agentState = common_vendor.ref({});
    const agentTurn = common_vendor.ref();
    const agentQuota = common_vendor.ref();
    const agentBusy = common_vendor.ref(false);
    const agentTyping = common_vendor.ref(false);
    const thinkingSteps = ["读取你的口味和忌口", "看看冰箱和朋友的情况", "组织这一轮的回答"];
    const thinkingStepIndex = common_vendor.ref(-1);
    let thinkingTimer;
    let typingTimer;
    function startThinking() {
      stopThinking();
      thinkingStepIndex.value = 0;
      thinkingTimer = setInterval(() => {
        if (thinkingStepIndex.value < thinkingSteps.length - 1)
          thinkingStepIndex.value += 1;
        else
          clearInterval(thinkingTimer);
      }, 800);
    }
    function stopThinking() {
      if (thinkingTimer) {
        clearInterval(thinkingTimer);
        thinkingTimer = void 0;
      }
      thinkingStepIndex.value = -1;
    }
    function stopTyping() {
      if (typingTimer) {
        clearInterval(typingTimer);
        typingTimer = void 0;
      }
      agentTyping.value = false;
    }
    const householdSelection = common_vendor.ref([]);
    const fridgeSelection = common_vendor.ref([]);
    const otherInput = common_vendor.ref(false);
    const agentCards = common_vendor.computed(() => {
      var _a, _b;
      const cards = (_a = agentTurn.value) == null ? void 0 : _a.cards;
      if (cards == null ? void 0 : cards.length)
        return cards.slice(0, 1);
      return ((_b = agentTurn.value) == null ? void 0 : _b.card) ? [agentTurn.value.card] : [];
    });
    const householdPickerActive = common_vendor.computed(() => {
      var _a, _b;
      if (((_a = agentTurn.value) == null ? void 0 : _a.action) !== "ASK_HOUSEHOLD")
        return false;
      const options = ((_b = agentCards.value[0]) == null ? void 0 : _b.options) || [];
      return options.some((option) => /^(household=|elder=|child=|pregnant=)/.test(option.value));
    });
    let messageId = 0;
    let progressTimer;
    let agentConversationId = "";
    const agentSteps = [
      { title: "读取锅仔记忆", copy: "口味、忌口和最近做过的菜" },
      { title: "检查家庭情况", copy: "老人、小孩和吃辣程度" },
      { title: "检查本周安排", copy: "做饭日期和每天菜数" },
      { title: "搭配主菜与配菜", copy: "避开拒绝过的菜，减少重复" },
      { title: "合并买菜清单", copy: "复用食材，整理成一张清单" }
    ];
    const isMember = common_vendor.computed(() => userStore.isActiveMember);
    const webSearchEnabled = common_vendor.ref(false);
    const canUseAgent = common_vendor.computed(() => {
      var _a;
      return isMember.value || (((_a = agentQuota.value) == null ? void 0 : _a.remaining) || 0) > 0;
    });
    function toggleWebSearch() {
      if (!utils_memberGate.requireMember("meal_agent_web_search"))
        return;
      webSearchEnabled.value = !webSearchEnabled.value;
    }
    const hasConversation = common_vendor.computed(() => messages.value.length > 0);
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
      var _a;
      const state = agentState.value;
      return [
        state.hasElder ? "家有老人，菜品软烂少盐" : "",
        state.hasChild ? "家有小孩，少刺少骨、口味温和" : "",
        state.spiceLevel ? `吃辣程度：${state.spiceLevel}` : "",
        state.favoriteCuisine ? `偏爱${state.favoriteCuisine}` : "",
        ((_a = state.requestedIngredients) == null ? void 0 : _a.length) ? `指定食材：${state.requestedIngredients.join("、")}` : "",
        state.mealContext || ""
      ].filter(Boolean).join("；");
    });
    common_vendor.onShow(load);
    let pageAlive = true;
    common_vendor.onUnmounted(() => {
      pageAlive = false;
      stopProgress();
      stopThinking();
      stopTyping();
    });
    function conversationDraftKey() {
      var _a;
      const openid = userStore.openid || ((_a = userStore.userInfo) == null ? void 0 : _a.openid) || "guest";
      return `mrc_meal_agent_draft:${openid}`;
    }
    function newConversationId() {
      return `agent-${Date.now()}-${Math.random().toString(36).slice(2, 10)}`;
    }
    function currentConversationId() {
      if (!agentConversationId)
        agentConversationId = newConversationId();
      return agentConversationId;
    }
    function persistConversation() {
      if (!agentConversationId)
        return;
      const draft = {
        conversationId: currentConversationId(),
        messages: messages.value.slice(-240),
        state: agentState.value,
        turn: agentTurn.value,
        completed: completed.value,
        generatedPlanId: generatedPlanId.value
      };
      common_vendor.index.setStorageSync(conversationDraftKey(), draft);
    }
    function restoreConversation() {
      const raw = common_vendor.index.getStorageSync(conversationDraftKey());
      const draft = typeof raw === "string" ? (() => {
        try {
          return JSON.parse(raw);
        } catch {
          return void 0;
        }
      })() : raw;
      if (!(draft == null ? void 0 : draft.conversationId) || !Array.isArray(draft.messages)) {
        agentConversationId = newConversationId();
        return;
      }
      agentConversationId = draft.conversationId;
      messages.value = draft.messages.filter((message) => message && (message.role === "agent" || message.role === "user") && typeof message.text === "string").map((message) => ({ ...message, selected: false }));
      messageId = messages.value.reduce((max, message) => Math.max(max, Number(message.id) || 0), 0);
      agentState.value = draft.state || {};
      agentTurn.value = draft.turn;
      completed.value = Boolean(draft.completed);
      generatedPlanId.value = draft.generatedPlanId;
      people.value = agentState.value.people || people.value;
      cookingDays.value = agentState.value.cookingDays || cookingDays.value;
      dishesPerDay.value = agentState.value.dishesPerDay || dishesPerDay.value;
      healthGoal.value = agentState.value.healthGoal || healthGoal.value;
      budget.value = agentState.value.budget || budget.value;
      hasElder.value = agentState.value.hasElder ?? hasElder.value;
      hasChild.value = agentState.value.hasChild ?? hasChild.value;
      spiceLevel.value = agentState.value.spiceLevel || spiceLevel.value;
      sessionCuisine.value = agentState.value.favoriteCuisine || sessionCuisine.value;
    }
    function restoreRemoteConversation(snapshot) {
      var _a;
      agentConversationId = snapshot.conversationId || currentConversationId();
      const restored = (snapshot.messages || []).filter((message) => message && (message.role === "agent" || message.role === "user") && typeof message.text === "string");
      const turnReply = (((_a = snapshot.turn) == null ? void 0 : _a.reply) || "").trim();
      const lastAgent = [...restored].reverse().find((message) => message.role === "agent");
      if (turnReply && (!lastAgent || lastAgent.text !== turnReply))
        restored.push({ role: "agent", text: turnReply });
      messages.value = restored.map((message, index) => ({ ...message, id: index + 1, selected: false }));
      messageId = messages.value.length;
      agentState.value = snapshot.state || {};
      agentTurn.value = snapshot.turn || void 0;
      people.value = agentState.value.people || people.value;
      cookingDays.value = agentState.value.cookingDays || cookingDays.value;
      dishesPerDay.value = agentState.value.dishesPerDay || dishesPerDay.value;
      healthGoal.value = agentState.value.healthGoal || healthGoal.value;
      budget.value = agentState.value.budget || budget.value;
      hasElder.value = agentState.value.hasElder ?? hasElder.value;
      hasChild.value = agentState.value.hasChild ?? hasChild.value;
      spiceLevel.value = agentState.value.spiceLevel || spiceLevel.value;
      sessionCuisine.value = agentState.value.favoriteCuisine || sessionCuisine.value;
    }
    async function load() {
      var _a, _b, _c;
      loading.value = true;
      try {
        await utils_login.ensureLogin();
        await utils_login.refreshUserInfo(true);
      } catch (error) {
        utils_toast.toastError(error, "登录后才能安排菜单");
        loading.value = false;
        return;
      }
      const [memoryResult, planResult, quotaResult, conversationResult] = await Promise.allSettled([api_preferences.fetchFoodMemory(), api_weeklyPlans.getCurrentPlan(), api_weeklyPlans.fetchAgentConversationQuota(), api_weeklyPlans.fetchCurrentAgentConversation()]);
      memory.value = memoryResult.status === "fulfilled" ? memoryResult.value : void 0;
      currentPlan.value = planResult.status === "fulfilled" ? planResult.value : void 0;
      agentQuota.value = quotaResult.status === "fulfilled" ? quotaResult.value : void 0;
      if (conversationResult.status === "fulfilled" && ((_b = (_a = conversationResult.value) == null ? void 0 : _a.messages) == null ? void 0 : _b.length))
        restoreRemoteConversation(conversationResult.value);
      else
        restoreConversation();
      if ((_c = memory.value) == null ? void 0 : _c.explicit.healthGoal)
        healthGoal.value = memory.value.explicit.healthGoal;
      if (canUseAgent.value && !messages.value.length) {
        addAgent(agentGreeting.value, memoryTags.value);
        const initialPrompt = utils_safeDecodePrompt.safeDecodePrompt(route.query.prompt);
        await runAgent(initialPrompt, Boolean(initialPrompt));
      }
      loading.value = false;
    }
    function addAgent(text, tags) {
      messages.value.push({ id: ++messageId, role: "agent", text: localizeAgentText(text), tags });
    }
    async function startNewConversation() {
      if (agentBusy.value || loading.value)
        return;
      try {
        await api_weeklyPlans.resetAgentConversation();
      } catch {
      }
      agentConversationId = newConversationId();
      messages.value = [];
      messageId = 0;
      agentTurn.value = void 0;
      agentState.value = {};
      quickReplies.value = [];
      rating.value = "";
      otherInput.value = false;
      completed.value = false;
      generatedPlanId.value = void 0;
      persistConversation();
      addAgent(agentGreeting.value, memoryTags.value);
      await scrollToLatest();
    }
    async function offerNewConversation() {
      const confirmed = await new Promise((resolve) => {
        common_vendor.index.showModal({
          title: "这轮对话太长啦",
          content: "开启一轮新对话吧，锅仔记住的口味偏好还会一直在。",
          confirmText: "新对话",
          cancelText: "暂不",
          success: (res) => resolve(Boolean(res.confirm)),
          fail: () => resolve(false)
        });
      });
      if (confirmed)
        await startNewConversation();
    }
    function localizeAgentText(text) {
      return text.replace(/\bbudget\b/gi, "预算").replace(/\bhealthGoal\b/gi, "饮食目标").replace(/\bmealContext\b/gi, "用餐场景").replace(/\bSAVE\b/g, "省钱").replace(/\bDAILY\b/g, "日常").replace(/\bTREAT\b/g, "丰盛").replace(/\bFITNESS\b/g, "均衡").replace(/\bLEAN\b/g, "清淡").replace(/\bBALANCED\b/g, "均衡");
    }
    async function scrollToLatest() {
      await common_vendor.nextTick$1();
      common_vendor.index.pageScrollTo({ scrollTop: 999999, duration: 220 });
    }
    async function runAgent(message, echo = false, echoLabel = message, webSearch = webSearchEnabled.value, echoMode = "text", preset) {
      if (agentBusy.value || agentTyping.value)
        return;
      if (echo && message)
        messages.value.push({ id: ++messageId, role: "user", text: echoLabel, selected: echoMode === "option" });
      agentBusy.value = true;
      quickReplies.value = [];
      startThinking();
      await scrollToLatest();
      try {
        const history = messages.value.slice(-240).map(({ role, text, tags, selected, card }) => ({ role, text, tags, selected, card }));
        const turn = await api_weeklyPlans.runMealAgentTurn(message, agentState.value, currentConversationId(), history, webSearch, preset);
        stopThinking();
        agentTurn.value = turn;
        otherInput.value = false;
        agentState.value = turn.state;
        householdSelection.value = turn.action === "ASK_HOUSEHOLD" ? [turn.state.hasElder ? "elder" : "", turn.state.hasChild ? "child" : ""].filter(Boolean) : [];
        fridgeSelection.value = [];
        people.value = turn.state.people || people.value;
        cookingDays.value = turn.state.cookingDays || [];
        dishesPerDay.value = turn.state.dishesPerDay || dishesPerDay.value;
        healthGoal.value = turn.state.healthGoal || healthGoal.value;
        budget.value = turn.state.budget || budget.value;
        hasElder.value = turn.state.hasElder ?? hasElder.value;
        hasChild.value = turn.state.hasChild ?? hasChild.value;
        spiceLevel.value = turn.state.spiceLevel || spiceLevel.value;
        sessionCuisine.value = turn.state.favoriteCuisine || sessionCuisine.value;
        await streamAgentReply(turn.reply);
        quickReplies.value = (turn.followups || []).map((text) => text.trim()).filter(Boolean).slice(0, 3);
        persistConversation();
      } catch (error) {
        stopThinking();
        stopTyping();
        const message2 = String((error == null ? void 0 : error.message) || "");
        if (message2.includes("重新开始") || message2.includes("过长")) {
          await offerNewConversation();
          return;
        }
        utils_toast.toastError(error, message2.includes("签到") || message2.includes("用完") ? "今日签到赠送的对话已用完，明天再来" : "锅仔刚刚走神了，请再说一次");
      } finally {
        agentBusy.value = false;
        stopThinking();
        await scrollToLatest();
      }
    }
    async function streamAgentReply(reply) {
      const full = localizeAgentText(reply || "");
      const lastAgent = [...messages.value].reverse().find((m) => m.role === "agent");
      if (full && (lastAgent == null ? void 0 : lastAgent.text) === full)
        return;
      const index = messages.value.length;
      messages.value.push({ id: ++messageId, role: "agent", text: "" });
      await scrollToLatest();
      if (!full)
        return;
      agentTyping.value = true;
      if (parseTeachingReply(full).steps.length) {
        messages.value[index].text = full;
        agentTyping.value = false;
        return;
      }
      const chars = Array.from(full);
      const step = Math.max(1, Math.round(chars.length / 90));
      await new Promise((resolve) => {
        let i = 0;
        typingTimer = setInterval(() => {
          i = Math.min(chars.length, i + step);
          messages.value[index].text = chars.slice(0, i).join("");
          if (i >= chars.length) {
            clearInterval(typingTimer);
            typingTimer = void 0;
            agentTyping.value = false;
            resolve();
          }
        }, 28);
      });
    }
    const quickReplies = common_vendor.ref([]);
    async function sendQuickReply(text) {
      await runAgent(text, true, text, webSearchEnabled.value, "option");
    }
    const SCENE_PRESETS = [
      { key: "SINGLE", label: "🍳 单人快煮", message: "就我一个人吃饭，安排这一周的晚餐，简单省事不剩饭" },
      { key: "COUPLE", label: "💑 情侣二人食", message: "我们两个人一起吃饭，安排这一周的晚餐" },
      { key: "BANQUET", label: "🎉 宴席硬菜", message: "周末家里来客人，帮我安排一桌宴席硬菜" },
      { key: "FRIDGE", label: "🧊 冰箱清空", message: "冰箱里还剩哪些食材？帮我用现有的食材安排菜单" }
    ];
    async function runPreset(preset) {
      await runAgent(preset.message, true, preset.label, webSearchEnabled.value, "option", preset.key);
    }
    const scenePresetsVisible = common_vendor.computed(() => canUseAgent.value && !completed.value && !generating.value);
    const TEACH_SOURCES = {
      "baike.baidu.com": "百度百科",
      "xiachufang.com": "下厨房",
      "meishij.net": "美食杰",
      "douguo.com": "豆果美食"
    };
    function parseTeachingReply(raw) {
      const text = (raw || "").trim();
      const result = { intro: "", steps: [], source: "" };
      if (!text)
        return result;
      let body = text;
      const sourceMatch = text.match(/[（(]来源[：:]\s*([^）)]+)[）)]\s*$/);
      if (sourceMatch) {
        body = text.slice(0, sourceMatch.index).trim();
        const rawSource = sourceMatch[1].trim();
        if (/^https?:\/\//i.test(rawSource)) {
          const host = Object.keys(TEACH_SOURCES).find((key) => rawSource.includes(key));
          result.source = host ? TEACH_SOURCES[host] : "网络资料";
        } else {
          result.source = rawSource;
        }
      }
      const numbered = body.split(/(?=(?:[1-9][.、）)]|[1-9]️⃣|步骤\s*[1-9]|①|②|③|④|⑤))/).map((s) => s.trim()).filter(Boolean);
      if (numbered.length >= 3 && body.length > 60) {
        result.steps = numbered.map((s) => s.replace(/^[1-9][.、）)]\s*|^[1-9]️⃣\s*|^步骤\s*[1-9][：:.、]?\s*|^[①②③④⑤]\s*/, ""));
        return result;
      }
      const sentences = body.split(/[；;。]/).map((s) => s.trim()).filter((s) => s.length >= 8);
      if (sentences.length >= 3 && sentences.length <= 8 && body.length > 50) {
        result.steps = sentences;
        return result;
      }
      result.intro = body;
      return result;
    }
    const GOAL_LABELS = {
      LEAN: "清淡",
      FITNESS: "均衡",
      BALANCED: "均衡",
      DAILY: "日常",
      TREAT: "丰盛",
      SAVE: "省钱",
      // 历史会话/模型自由文本的兜底映射，避免「目标:mild_flavor」这类原文漏出
      MILD_FLAVOR: "清淡",
      LIGHT: "清淡",
      RICH: "丰盛",
      CHEAP: "省钱"
    };
    const SPICE_LABELS = {
      NONE: "不辣",
      MILD: "微辣",
      NORMAL: "中辣",
      MEDIUM: "中辣",
      HOT: "能吃辣",
      SPICY: "能吃辣",
      不辣: "不辣",
      微辣: "微辣",
      中辣: "中辣",
      能吃辣: "能吃辣"
    };
    function labelValue(map, value) {
      return map[value] || map[value.toUpperCase()] || map[value.toLowerCase()] || localizeAgentText(value);
    }
    const memoryChips = common_vendor.computed(() => {
      var _a;
      const state = agentState.value || {};
      const chips = [];
      if (state.people)
        chips.push({ key: "people", label: `${state.people} 人吃`, intent: "人数要调整一下" });
      if (state.hasElder)
        chips.push({ key: "elder", label: "家有老人", intent: "家庭情况要调整一下" });
      if (state.hasChild)
        chips.push({ key: "child", label: "家有小孩", intent: "家庭情况要调整一下" });
      if (state.spiceLevel)
        chips.push({ key: "spice", label: `口味:${labelValue(SPICE_LABELS, state.spiceLevel)}`, intent: "口味要改一下，重新说说辣度" });
      if (state.favoriteCuisine)
        chips.push({ key: "cuisine", label: `偏爱${state.favoriteCuisine}`, intent: "想换个菜系" });
      if (state.healthGoal)
        chips.push({ key: "goal", label: `目标:${labelValue(GOAL_LABELS, state.healthGoal)}`, intent: "饮食目标要调整" });
      if (state.budget)
        chips.push({ key: "budget", label: `预算:${labelValue(GOAL_LABELS, state.budget)}`, intent: "预算标准要调整" });
      if ((_a = state.cookingDays) == null ? void 0 : _a.length)
        chips.push({ key: "days", label: `做 ${state.cookingDays.length} 天`, intent: "做饭的天数要调整" });
      if (state.dishesPerDay)
        chips.push({ key: "dishes", label: `每天 ${state.dishesPerDay} 道`, intent: "每天做的菜数要调整" });
      for (const item of state.requestedIngredients || [])
        chips.push({ key: `want-${item}`, label: `想吃${item}`, intent: `不想吃${item}了，换点别的` });
      return chips;
    });
    async function onMemoryChipTap(intent) {
      await runAgent(intent, true, intent, webSearchEnabled.value, "option");
    }
    function householdValue(value) {
      if (value === "elder=yes" || value.includes("elder"))
        return "elder";
      if (value === "child=yes" || value.includes("child"))
        return "child";
      if (value === "pregnant=yes" || value.includes("pregnant"))
        return "pregnant";
      if (value.includes("adult") || value.includes("none"))
        return "adult";
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
      const adult = householdSelection.value.includes("adult") || householdSelection.value.includes("none");
      const value = adult ? "household=adult" : `household=${householdSelection.value.join(",")}`;
      const label = adult ? "都是成人" : householdSelection.value.map((item) => item === "elder" ? "有老人" : item === "child" ? "有小孩" : "有孕妇").join("、");
      await runAgent(value, true, label, webSearchEnabled.value, "option");
    }
    async function submitComposer() {
      const text = composerText.value.trim();
      if (!text)
        return;
      composerText.value = "";
      await runAgent(text, true);
    }
    async function selectCardOption(value, label) {
      if (value === "other") {
        otherInput.value = true;
        return;
      }
      await runAgent(value, true, label, webSearchEnabled.value, "option");
    }
    function readyGenerateOptions(card) {
      return card.options.filter((option) => option.value === "generate");
    }
    function readyOtherOptions(card) {
      return card.options.filter((option) => option.value !== "generate");
    }
    async function onCardOptionChange(card, value) {
      var _a;
      const picked = Array.isArray(value) ? value[0] : value;
      if (!picked)
        return;
      if (picked === "generate") {
        generate();
        return;
      }
      const label = ((_a = card.options.find((option) => option.value === picked)) == null ? void 0 : _a.label) || picked;
      await selectCardOption(picked, label);
    }
    function toggleFridgeItem(value) {
      if (value === "fridge_priority=soon") {
        fridgeSelection.value = [value];
        return;
      }
      fridgeSelection.value = fridgeSelection.value.filter((item) => item !== "fridge_priority=soon");
      fridgeSelection.value = fridgeSelection.value.includes(value) ? fridgeSelection.value.filter((item) => item !== value) : [...fridgeSelection.value, value];
    }
    async function confirmFridgeSelection() {
      const priority = !fridgeSelection.value.length || fridgeSelection.value[0] === "fridge_priority=soon";
      const values = priority ? fridgeSelection.value : [`fridge_select=${fridgeSelection.value.map((value) => value.replace(/^fridge_item=/, "")).join(",")}`];
      const label = priority ? "优先消耗临期食材" : `选用：${fridgeSelection.value.map((value) => value.replace(/^fridge_item=/, "")).join("、")}`;
      await runAgent(values[0], true, label, webSearchEnabled.value, "option");
    }
    async function submitOther() {
      const text = composerText.value.trim();
      if (!text)
        return;
      composerText.value = "";
      otherInput.value = false;
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
        const job = await api_weeklyPlans.generateWeeklyPlan({
          people: people.value,
          days: cookingDays.value.length,
          cookingDays: cookingDays.value,
          healthGoal: healthGoal.value,
          dishesPerDay: dishesPerDay.value,
          budget: budget.value,
          conversationNotes: conversationNotes.value,
          sendNotification: notify
        });
        const planId = await api_weeklyPlans.pollWeeklyPlanJob(job.jobId, 9e4, 2e3, () => !pageAlive);
        stopProgress();
        activeStep.value = agentSteps.length;
        completed.value = true;
        if (!planId) {
          if (pageAlive)
            utils_toast.toastError(null, "锅仔还在收尾这周的菜单，稍后可在备餐档案里查看");
          return;
        }
        generatedPlanId.value = planId;
        persistConversation();
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
        await api_weeklyPlans.rateAgentConversation(value);
        if (value !== "满意")
          common_vendor.index.showToast({ title: "锅仔记下了，下次会更懂你", icon: "none", duration: 2200 });
        else
          common_vendor.index.showToast({ title: "收到！锅仔继续保持", icon: "none", duration: 1800 });
      } catch {
      }
    }
    function onRatingChange(value) {
      const ratingValue = Array.isArray(value) ? value[0] : value;
      if (ratingValue)
        void rateConversation(ratingValue);
    }
    async function openGeneratedPlan() {
      if (generatedPlanId.value) {
        router.replace({ name: "weekly-plan-detail", params: { id: String(generatedPlanId.value) } });
        return;
      }
      try {
        const plan = await api_weeklyPlans.getCurrentPlan();
        if (plan == null ? void 0 : plan.id) {
          generatedPlanId.value = plan.id;
          router.replace({ name: "weekly-plan-detail", params: { id: String(plan.id) } });
        }
      } catch {
      }
    }
    function openCurrentPlan() {
      if (currentPlan.value)
        router.push({ name: "weekly-plan-detail", params: { id: String(currentPlan.value.id) } });
    }
    function openMembership() {
      router.push({ name: "membership" });
    }
    function openGallery() {
      router.push({ name: "gallery" });
    }
    common_vendor.onShareAppMessage(() => {
      const sharer = userStore.openid;
      const path = `/pages/meal-agent/index${sharer ? `?sharer=${encodeURIComponent(sharer)}` : ""}`;
      void api_share.recordShare("meal-agent").catch(() => {
      });
      return {
        title: "锅仔管饭：这一周交给锅仔安排，少想一点认真吃饭",
        path
      };
    });
    common_vendor.onShareTimeline(() => {
      const sharer = userStore.openid;
      return {
        title: "锅仔管饭：这一周交给锅仔安排，少想一点认真吃饭",
        query: sharer ? `sharer=${encodeURIComponent(sharer)}` : ""
      };
    });
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
        h: canUseAgent.value && (generating.value || completed.value)
      }, canUseAgent.value && (generating.value || completed.value) ? common_vendor.e({
        i: common_vendor.t(completed.value ? "这一周已经排好" : isMember.value ? "锅仔正在安排这一周" : "正在生成基础菜单"),
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
        l: completed.value && isMember.value
      }, completed.value && isMember.value ? {
        m: common_vendor.o(onRatingChange),
        n: common_vendor.p({
          ["model-value"]: rating.value,
          options: RATING_OPTIONS,
          columns: 3,
          ["aria-label"]: "为这次对话评分"
        }),
        o: common_vendor.o(openGeneratedPlan),
        p: common_vendor.p({
          variant: "primary",
          ["aria-label"]: "查看这周菜单"
        })
      } : completed.value ? {
        r: common_vendor.o(openGeneratedPlan),
        s: common_vendor.p({
          variant: "primary",
          ["aria-label"]: "查看这周菜单"
        })
      } : {}, {
        q: completed.value
      }) : {}, {
        t: canUseAgent.value && (generating.value || completed.value)
      }, canUseAgent.value && (generating.value || completed.value) ? common_vendor.e({
        v: agentBusy.value || agentTyping.value,
        w: common_vendor.o(submitComposer),
        x: composerText.value,
        y: common_vendor.o(($event) => composerText.value = $event.detail.value),
        z: !agentBusy.value && !agentTyping.value
      }, !agentBusy.value && !agentTyping.value ? {
        A: common_vendor.p({
          name: "send",
          size: 30,
          color: "#fff"
        })
      } : {}, {
        B: common_vendor.o(submitComposer),
        C: common_vendor.p({
          variant: "primary",
          block: false,
          disabled: agentBusy.value || agentTyping.value || !composerText.value.trim(),
          loading: agentBusy.value || agentTyping.value,
          ["aria-label"]: agentBusy.value || agentTyping.value ? "锅仔正在思考" : "发送"
        })
      }) : canUseAgent.value || hasConversation.value ? common_vendor.e({
        E: !canUseAgent.value
      }, !canUseAgent.value ? {} : {}, {
        F: memoryChips.value.length
      }, memoryChips.value.length ? {
        G: common_vendor.f(memoryChips.value, (chip, k0, i0) => {
          return {
            a: common_vendor.t(chip.label),
            b: chip.key,
            c: common_vendor.o(($event) => onMemoryChipTap(chip.intent), chip.key)
          };
        }),
        H: agentBusy.value || agentTyping.value
      } : {}, {
        I: scenePresetsVisible.value
      }, scenePresetsVisible.value ? {
        J: common_vendor.f(SCENE_PRESETS, (preset, k0, i0) => {
          return {
            a: common_vendor.t(preset.label),
            b: preset.key,
            c: common_vendor.o(($event) => runPreset(preset), preset.key)
          };
        }),
        K: agentBusy.value || agentTyping.value
      } : {}, {
        L: common_vendor.f(messages.value, (message, k0, i0) => {
          var _a, _b;
          return common_vendor.e({
            a: message.role === "agent"
          }, message.role === "agent" ? {
            b: `${common_vendor.unref(utils_assets.STATIC_BASE_URL)}/static/guozai/action_08_peek.png`
          } : {}, {
            c: message.selected
          }, message.selected ? {
            d: common_vendor.t(message.text)
          } : common_vendor.e({
            e: parseTeachingReply(message.text).steps.length
          }, parseTeachingReply(message.text).steps.length ? common_vendor.e({
            f: common_vendor.f(parseTeachingReply(message.text).steps, (step, stepIdx, i1) => {
              return {
                a: common_vendor.t(stepIdx + 1),
                b: common_vendor.t(step),
                c: stepIdx
              };
            }),
            g: parseTeachingReply(message.text).source
          }, parseTeachingReply(message.text).source ? {
            h: common_vendor.t(parseTeachingReply(message.text).source)
          } : {}) : common_vendor.e({
            i: common_vendor.t(message.text),
            j: (_a = message.tags) == null ? void 0 : _a.length
          }, ((_b = message.tags) == null ? void 0 : _b.length) ? {
            k: common_vendor.f(message.tags, (tag, k1, i1) => {
              return {
                a: common_vendor.t(tag),
                b: tag
              };
            })
          } : {}), {
            l: message.card
          }, message.card ? common_vendor.e({
            m: common_vendor.t(message.card.title),
            n: message.card.description
          }, message.card.description ? {
            o: common_vendor.t(message.card.description)
          } : {}, {
            p: common_vendor.f(message.card.options, (option, k1, i1) => {
              return {
                a: common_vendor.t(option.label),
                b: option.value
              };
            })
          }) : {}), {
            q: message.id,
            r: common_vendor.n(`chat-row--${message.role}`),
            s: common_vendor.n({
              "chat-row--selection": message.selected
            })
          });
        }),
        M: agentBusy.value && thinkingStepIndex.value >= 0
      }, agentBusy.value && thinkingStepIndex.value >= 0 ? {
        N: `${common_vendor.unref(utils_assets.STATIC_BASE_URL)}/static/guozai/action_08_peek.png`,
        O: common_vendor.f(thinkingSteps, (step, idx, i0) => {
          return common_vendor.e({
            a: idx < thinkingStepIndex.value
          }, idx < thinkingStepIndex.value ? {} : idx === thinkingStepIndex.value ? {} : {
            c: common_vendor.t(idx + 1)
          }, {
            b: idx === thinkingStepIndex.value,
            d: common_vendor.t(step),
            e: step,
            f: idx < thinkingStepIndex.value ? 1 : "",
            g: idx === thinkingStepIndex.value ? 1 : ""
          });
        })
      } : {}, {
        P: !agentBusy.value && !agentTyping.value && quickReplies.value.length && !agentCards.value.length
      }, !agentBusy.value && !agentTyping.value && quickReplies.value.length && !agentCards.value.length ? {
        Q: common_vendor.f(quickReplies.value, (reply, k0, i0) => {
          return {
            a: common_vendor.t(reply),
            b: reply,
            c: common_vendor.o(($event) => sendQuickReply(reply), reply)
          };
        })
      } : {}, {
        R: agentCards.value.length && !agentBusy.value
      }, agentCards.value.length && !agentBusy.value ? {
        S: common_vendor.f(agentCards.value, (card, cardIndex, i0) => {
          return common_vendor.e({
            a: common_vendor.t(card.type === "READY" ? "准备好了" : "锅仔想确认"),
            b: common_vendor.t(card.title),
            c: common_vendor.t(card.description),
            d: card.type === "CUISINE" || card.type === "READY"
          }, card.type === "CUISINE" || card.type === "READY" ? {
            e: `${common_vendor.unref(utils_assets.STATIC_BASE_URL)}/static/guozai/action_06_glasses.png`
          } : {}, {
            f: card.type === "READY"
          }, card.type === "READY" ? {} : {}, householdPickerActive.value ? common_vendor.e({
            g: common_vendor.f(card.options, (option, k1, i1) => {
              return {
                a: common_vendor.t(householdSelection.value.includes(householdValue(option.value)) ? "✓" : ""),
                b: common_vendor.t(option.label),
                c: option.value,
                d: householdSelection.value.includes(householdValue(option.value)) ? 1 : "",
                e: householdSelection.value.includes(householdValue(option.value)),
                f: common_vendor.o(($event) => option.value === "other" ? otherInput.value = true : toggleHousehold(option.value), option.value)
              };
            }),
            h: agentBusy.value,
            i: !householdSelection.value.length || agentBusy.value,
            j: common_vendor.o(confirmHousehold, `${card.title}-${cardIndex}`),
            k: otherInput.value
          }, otherInput.value ? common_vendor.e({
            l: agentBusy.value,
            m: common_vendor.o(submitOther, `${card.title}-${cardIndex}`),
            n: composerText.value,
            o: common_vendor.o(($event) => composerText.value = $event.detail.value, `${card.title}-${cardIndex}`),
            p: !agentBusy.value
          }, !agentBusy.value ? {
            q: "d8896d00-8-" + i0 + "," + ("d8896d00-7-" + i0),
            r: common_vendor.p({
              name: "send",
              size: 30,
              color: "#fff"
            })
          } : {}, {
            s: common_vendor.o(submitOther, `${card.title}-${cardIndex}`),
            t: "d8896d00-7-" + i0 + ",d8896d00-0",
            v: common_vendor.p({
              variant: "primary",
              block: false,
              disabled: agentBusy.value || !composerText.value.trim(),
              loading: agentBusy.value,
              ["aria-label"]: "发送自定义家庭情况"
            })
          }) : {}) : card.type === "FRIDGE_INVENTORY" ? {
            x: common_vendor.f(card.options, (option, k1, i1) => {
              return {
                a: common_vendor.t(fridgeSelection.value.includes(option.value) ? "✓" : ""),
                b: common_vendor.t(option.label),
                c: option.value,
                d: fridgeSelection.value.includes(option.value) ? 1 : "",
                e: common_vendor.o(($event) => toggleFridgeItem(option.value), option.value)
              };
            }),
            y: agentBusy.value,
            z: common_vendor.t(fridgeSelection.value.length ? "用这些食材安排菜谱" : "按临期优先安排菜谱"),
            A: agentBusy.value,
            B: common_vendor.o(confirmFridgeSelection, `${card.title}-${cardIndex}`)
          } : card.type === "READY" ? common_vendor.e({
            D: common_vendor.f(readyGenerateOptions(card), (option, k1, i1) => {
              return {
                a: common_vendor.t(option.label),
                b: option.value,
                c: common_vendor.o(generate, option.value),
                d: "d8896d00-9-" + i0 + "-" + i1 + ",d8896d00-0"
              };
            }),
            E: common_vendor.p({
              variant: "primary",
              ["aria-label"]: "开始生成本周菜单",
              disabled: agentBusy.value
            }),
            F: readyOtherOptions(card).length
          }, readyOtherOptions(card).length ? {
            G: common_vendor.o((value) => onCardOptionChange(card, value), `${card.title}-${cardIndex}`),
            H: "d8896d00-10-" + i0 + ",d8896d00-0",
            I: common_vendor.p({
              options: readyOtherOptions(card),
              columns: 1,
              disabled: agentBusy.value,
              ["aria-label"]: "生成前再调整一下"
            })
          } : {}) : {
            J: common_vendor.o((value) => onCardOptionChange(card, value), `${card.title}-${cardIndex}`),
            K: "d8896d00-11-" + i0 + ",d8896d00-0",
            L: common_vendor.p({
              options: card.options,
              columns: 1,
              disabled: agentBusy.value,
              ["aria-label"]: card.title || "选择一个选项"
            })
          }, {
            w: card.type === "FRIDGE_INVENTORY",
            C: card.type === "READY"
          }, otherInput.value && !householdPickerActive.value ? common_vendor.e({
            M: agentBusy.value,
            N: common_vendor.o(submitOther, `${card.title}-${cardIndex}`),
            O: composerText.value,
            P: common_vendor.o(($event) => composerText.value = $event.detail.value, `${card.title}-${cardIndex}`),
            Q: !agentBusy.value
          }, !agentBusy.value ? {
            R: "d8896d00-13-" + i0 + "," + ("d8896d00-12-" + i0),
            S: common_vendor.p({
              name: "send",
              size: 30,
              color: "#fff"
            })
          } : {}, {
            T: common_vendor.o(submitOther, `${card.title}-${cardIndex}`),
            U: "d8896d00-12-" + i0 + ",d8896d00-0",
            V: common_vendor.p({
              variant: "primary",
              block: false,
              disabled: agentBusy.value || !composerText.value.trim(),
              loading: agentBusy.value,
              ["aria-label"]: "发送自定义回答"
            })
          }) : {}, {
            W: `${card.title}-${cardIndex}`,
            X: card.type === "CUISINE" ? 1 : "",
            Y: card.type === "READY" ? 1 : ""
          });
        }),
        T: householdPickerActive.value,
        U: otherInput.value && !householdPickerActive.value
      } : {}, {
        V: common_vendor.p({
          name: "search",
          size: 26,
          color: webSearchEnabled.value ? "#fff" : isMember.value ? "#EF5A3C" : "#B9A99C"
        }),
        W: !isMember.value
      }, !isMember.value ? {} : {}, {
        X: webSearchEnabled.value ? 1 : "",
        Y: !isMember.value ? 1 : "",
        Z: isMember.value ? "联网搜索开关" : "会员专享：联网搜索",
        aa: common_vendor.o(toggleWebSearch),
        ab: agentBusy.value || agentTyping.value || !canUseAgent.value,
        ac: common_vendor.o(submitComposer),
        ad: composerText.value,
        ae: common_vendor.o(($event) => composerText.value = $event.detail.value),
        af: !agentBusy.value && !agentTyping.value
      }, !agentBusy.value && !agentTyping.value ? {
        ag: common_vendor.p({
          name: "send",
          size: 30,
          color: "#fff"
        })
      } : {}, {
        ah: common_vendor.o(submitComposer),
        ai: common_vendor.p({
          variant: "primary",
          block: false,
          disabled: agentBusy.value || agentTyping.value || !canUseAgent.value || !composerText.value.trim(),
          loading: agentBusy.value || agentTyping.value,
          ["aria-label"]: agentBusy.value || agentTyping.value ? "锅仔正在思考" : "发送"
        }),
        aj: common_vendor.o(($event) => common_vendor.unref(router).push({
          name: "weekly-plan"
        })),
        ak: common_vendor.o(startNewConversation),
        al: !canUseAgent.value ? 1 : ""
      }) : {
        am: common_vendor.o(openGallery),
        an: common_vendor.f([1, 2, 3, 4], (value, k0, i0) => {
          return {
            a: common_vendor.t(value),
            b: value,
            c: people.value === value ? 1 : "",
            d: common_vendor.o(($event) => people.value = value, value)
          };
        }),
        ao: common_vendor.f([1, 2, 3], (value, k0, i0) => {
          return {
            a: common_vendor.t(value),
            b: value,
            c: dishesPerDay.value === value ? 1 : "",
            d: common_vendor.o(($event) => dishesPerDay.value = value, value)
          };
        }),
        ap: generating.value,
        aq: common_vendor.o(generate),
        ar: common_vendor.o(openMembership)
      }, {
        D: canUseAgent.value || hasConversation.value
      }));
    };
  }
});
_sfc_defineComponent.__runtimeHooks = 6;
const MiniProgramPage = /* @__PURE__ */ common_vendor._export_sfc(_sfc_defineComponent, [["__scopeId", "data-v-d8896d00"]]);
exports.MiniProgramPage = MiniProgramPage;
