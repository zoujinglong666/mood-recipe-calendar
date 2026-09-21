"use strict";
const common_vendor = require("../../common/vendor.js");
const composables_useNavBar = require("../../composables/useNavBar.js");
const utils_assets = require("../../utils/assets.js");
const api_recipes = require("../../api/recipes.js");
const api_virtualCommerce = require("../../api/virtualCommerce.js");
const utils_albumShare = require("../../utils/albumShare.js");
const utils_cookingDraft = require("../../utils/cookingDraft.js");
const utils_login = require("../../utils/login.js");
const utils_toast = require("../../utils/toast.js");
const __unplugin_components_1 = () => "../../node-modules/@wot-ui/ui/components/wd-image-preview/wd-image-preview.js";
const __unplugin_components_0 = () => "../../node-modules/@wot-ui/ui/components/wd-navbar/wd-navbar.js";
if (!Array) {
  const _component_wd_navbar = __unplugin_components_0;
  const _component_wd_image_preview = __unplugin_components_1;
  const _component_layout_default_uni = common_vendor.resolveComponent("layout-default-uni");
  (_component_wd_navbar + _component_wd_image_preview + _component_layout_default_uni)();
}
if (!Math) {
  (ErrorState + Icon)();
}
const Icon = () => "../../components/common/Icon.js";
const ErrorState = () => "../../components/guozai/ErrorState.js";
const POLL_INTERVAL = 900;
const POLL_TIMEOUT = 9e4;
const MAX_POLL_FAILURES = 3;
const _sfc_defineComponent = common_vendor.defineComponent({
  __name: "index",
  setup(__props) {
    const route = common_vendor.useRoute();
    const router = common_vendor.useRouter();
    const { previewImage } = common_vendor.useImagePreview();
    const mood = common_vendor.computed(() => route.query.mood || "开心");
    const linkedRecipeId = common_vendor.computed(() => Number(route.query.recipeId) || 0);
    const HEALING_TEXTS = {
      开心: "你今天的好心情，适合配一口热乎又满足的。",
      平静: "不赶时间的这一餐，就让味道慢慢展开。",
      疲惫: "今天辛苦了，选一道省心又暖胃的给你。",
      焦虑: "先把注意力交给锅里升起的香气，慢慢来。",
      难过: "不用急着振作，先认真吃一顿温暖的饭。",
      嘴馋: "既然想吃点好的，就选一道香气很有存在感的。",
      低落: "热饭会稳稳接住今天的你，先吃饱再说。",
      想家: "熟悉的家常味最会安慰人，这道很适合今天。",
      期待: "把期待放进锅里，今晚值得一顿有仪式感的饭。",
      满足: "此刻刚刚好，用一道舒服的菜延续这份满足。",
      得意: "今天这么棒，当然要用一道拿手菜奖励自己。",
      害羞: "不用说很多，让一顿认真做的饭替你表达。"
    };
    const loading = common_vendor.ref(false);
    const error = common_vendor.ref("");
    const recipe = common_vendor.ref(null);
    const recommendationJob = common_vendor.ref(null);
    const showToolTrace = common_vendor.ref(false);
    const imageFailed = common_vendor.ref(false);
    const showSteps = common_vendor.ref(false);
    const showAiPanel = common_vendor.ref(false);
    const deepIngredients = common_vendor.ref("");
    const deepMinutes = common_vendor.ref("30");
    const deepPreference = common_vendor.ref("");
    const deepLoading = common_vendor.ref(false);
    const productsLoading = common_vendor.ref(false);
    const productsLoaded = common_vendor.ref(false);
    const productsError = common_vendor.ref("");
    const purchasingSku = common_vendor.ref("");
    const aiProducts = common_vendor.ref([]);
    const feedbackLoading = common_vendor.ref("");
    const feedbackState = common_vendor.ref({ liked: false, disliked: false, made: false });
    const showShareSheet = common_vendor.ref(false);
    const shareCardLoading = common_vendor.ref(false);
    const shareCardPaths = common_vendor.ref({});
    const shareCardError = common_vendor.ref("");
    const shareCardSaved = common_vendor.ref(false);
    const shareStyle = common_vendor.ref("classic");
    const selectedShareCardPath = common_vendor.computed(() => shareCardPaths.value[shareStyle.value] || "");
    let pollTimer;
    let pollRun = 0;
    let pollStartedAt = 0;
    let pollFailures = 0;
    let resumeRecommendation = false;
    function parseStringList(value) {
      if (!value)
        return [];
      try {
        const parsed = JSON.parse(value);
        return Array.isArray(parsed) ? parsed.map(String).filter(Boolean) : [];
      } catch {
        return [];
      }
    }
    const ingredients = common_vendor.computed(() => {
      var _a;
      return parseStringList((_a = recipe.value) == null ? void 0 : _a.ingredients);
    });
    const steps = common_vendor.computed(() => {
      var _a;
      return parseStringList((_a = recipe.value) == null ? void 0 : _a.steps);
    });
    const feedbackAvailable = common_vendor.computed(() => {
      var _a, _b;
      return Number((_a = recipe.value) == null ? void 0 : _a.id) > 0 || Boolean((_b = recipe.value) == null ? void 0 : _b.exposureId);
    });
    const recipeImageAvailable = common_vendor.computed(() => {
      var _a;
      return Boolean((_a = recipe.value) == null ? void 0 : _a.image) && !imageFailed.value;
    });
    const healingText = common_vendor.computed(() => {
      var _a, _b, _c;
      return ((_b = (_a = recipe.value) == null ? void 0 : _a.recommendationReason) == null ? void 0 : _b.trim()) || HEALING_TEXTS[mood.value] || ((_c = recipe.value) == null ? void 0 : _c.description) || "好好吃饭，锅仔会陪你慢慢找到喜欢的味道。";
    });
    const primaryText = common_vendor.computed(() => steps.value.length ? "开始跟锅仔做" : "记下这顿");
    const toolTrace = common_vendor.computed(() => {
      var _a;
      return ((_a = recommendationJob.value) == null ? void 0 : _a.steps.filter((step) => step.status !== "WAITING")) || [];
    });
    function stepStatusText(step) {
      return {
        WAITING: "等待中",
        RUNNING: "进行中",
        COMPLETED: "已完成",
        DEGRADED: "已降级",
        FAILED: "未完成"
      }[step.status];
    }
    function stepSymbol(step) {
      return step.status === "COMPLETED" ? "✓" : step.status === "DEGRADED" ? "↪" : step.status === "FAILED" ? "!" : step.status === "RUNNING" ? "•••" : "·";
    }
    function readableError(errorValue, fallback) {
      const message = errorValue instanceof Error ? errorValue.message : String((errorValue == null ? void 0 : errorValue.message) || "");
      return !message || /request:fail|network|timeout/i.test(message) ? fallback : message;
    }
    async function loadRecipe() {
      if (loading.value)
        return;
      loading.value = true;
      error.value = "";
      recipe.value = null;
      feedbackState.value = { liked: false, disliked: false, made: false };
      recommendationJob.value = null;
      showToolTrace.value = false;
      imageFailed.value = false;
      showSteps.value = false;
      shareCardPaths.value = {};
      shareCardError.value = "";
      shareCardSaved.value = false;
      shareStyle.value = "classic";
      clearPollTimer();
      const run = ++pollRun;
      try {
        await utils_login.ensureLogin();
        if (linkedRecipeId.value) {
          recipe.value = await api_recipes.fetchRecipeDetail(linkedRecipeId.value);
          await loadFeedbackState();
          loading.value = false;
          return;
        }
        const created = await api_recipes.createRecommendationJob(mood.value);
        if (run !== pollRun)
          return;
        recommendationJob.value = created;
        pollStartedAt = Date.now();
        pollFailures = 0;
        applyJob(created, run);
      } catch (e) {
        if (run !== pollRun)
          return;
        error.value = readableError(e, "网络开小差了");
        loading.value = false;
      }
    }
    common_vendor.onLoad(loadRecipe);
    common_vendor.onHide(() => {
      if (loading.value)
        resumeRecommendation = true;
      stopPolling();
      loading.value = false;
    });
    common_vendor.onShow(() => {
      if (resumeRecommendation) {
        resumeRecommendation = false;
        loadRecipe();
      }
    });
    common_vendor.onUnload(stopPolling);
    common_vendor.onShareAppMessage(() => ({
      title: recipe.value ? `锅仔推荐：${recipe.value.name}，适合${mood.value}的今天` : "让锅仔按心情推荐今天吃什么",
      path: `/pages/recipe/index?mood=${encodeURIComponent(mood.value)}`
    }));
    common_vendor.onShareTimeline(() => ({
      title: recipe.value ? `今天吃${recipe.value.name}，锅仔说很适合${mood.value}的我` : "让锅仔按心情推荐今天吃什么",
      query: `mood=${encodeURIComponent(mood.value)}`
    }));
    function clearPollTimer() {
      if (pollTimer !== void 0) {
        clearTimeout(pollTimer);
        pollTimer = void 0;
      }
    }
    function stopPolling() {
      clearPollTimer();
      pollRun += 1;
    }
    function applyJob(job, run) {
      recommendationJob.value = job;
      if (job.status === "SUCCEEDED") {
        recipe.value = job.recipe || null;
        loading.value = false;
        if (!job.recipe)
          error.value = "推荐已经完成，但菜谱内容暂时不可用";
        else
          void loadFeedbackState();
        return;
      }
      if (job.status === "FAILED") {
        loading.value = false;
        error.value = job.message || "锅仔这次没想好，重新推荐一次吧";
        return;
      }
      if (Date.now() - pollStartedAt >= POLL_TIMEOUT) {
        loading.value = false;
        error.value = "这次推荐等得有点久，任务已停止自动查询";
        return;
      }
      pollTimer = setTimeout(() => pollRecommendation(job.jobId, run), POLL_INTERVAL);
    }
    async function pollRecommendation(jobId, run) {
      if (run !== pollRun)
        return;
      try {
        const current = await api_recipes.fetchRecommendationJob(jobId);
        if (run !== pollRun)
          return;
        pollFailures = 0;
        applyJob(current, run);
      } catch (e) {
        if (run !== pollRun)
          return;
        pollFailures += 1;
        if (pollFailures >= MAX_POLL_FAILURES || Date.now() - pollStartedAt >= POLL_TIMEOUT) {
          loading.value = false;
          error.value = readableError(e, "暂时读不到推荐进度，请重新试一次");
          return;
        }
        pollTimer = setTimeout(() => pollRecommendation(jobId, run), POLL_INTERVAL);
      }
    }
    async function revealSteps() {
      showSteps.value = true;
      await common_vendor.nextTick$1();
      common_vendor.index.pageScrollTo({ selector: "#recipe-steps", duration: 240 });
    }
    function toggleSteps() {
      if (showSteps.value)
        showSteps.value = false;
      else
        revealSteps();
    }
    function handlePrimaryAction() {
      if (steps.value.length)
        startCooking();
      else
        goRecord();
    }
    function startCooking() {
      if (!recipe.value || !steps.value.length) {
        utils_toast.toast("这道菜暂时没有完整步骤，先记下它吧");
        return;
      }
      utils_cookingDraft.saveCookingDraft(recipe.value, mood.value);
      router.push({ name: "cooking" });
    }
    function goRecord() {
      if (!recipe.value)
        return;
      utils_cookingDraft.saveRecordDraft(recipe.value, mood.value);
      router.pushTab({ name: "record" });
    }
    async function loadFeedbackState() {
      if (!recipe.value || !feedbackAvailable.value)
        return;
      try {
        feedbackState.value = await api_recipes.fetchRecipeFeedback(recipe.value);
      } catch {
        feedbackState.value = { liked: false, disliked: false, made: false };
      }
    }
    async function sendFeedback(action) {
      if (!recipe.value || !recipe.value.id && !recipe.value.exposureId || feedbackLoading.value)
        return;
      if (action === "MADE") {
        goRecord();
        return;
      }
      feedbackLoading.value = action;
      try {
        await utils_login.ensureLogin();
        feedbackState.value = await api_recipes.sendRecipeFeedback(recipe.value, action);
        if (action === "LIKE") {
          utils_toast.toast("锅仔记住啦，以后多推荐这类菜");
        } else {
          utils_toast.toast("明白，锅仔换一道更合胃口的");
          await loadRecipe();
        }
      } catch (e) {
        utils_toast.toastError(e, "记录偏好失败，请重试");
      } finally {
        feedbackLoading.value = "";
      }
    }
    function openShareSheet() {
      if (!recipe.value) {
        utils_toast.toast("先等锅仔推荐好一餐吧");
        return;
      }
      showShareSheet.value = true;
      shareCardPaths.value = {};
      shareCardError.value = "";
      shareCardSaved.value = false;
      shareStyle.value = "classic";
      void generateShareCards();
    }
    async function generateShareCards() {
      if (!recipe.value || shareCardLoading.value)
        return;
      shareCardLoading.value = true;
      shareCardError.value = "";
      shareCardSaved.value = false;
      shareCardPaths.value = {};
      try {
        await common_vendor.nextTick$1();
        const cards = {};
        const failed = [];
        for (const style of ["classic", "guozai"]) {
          try {
            cards[style] = await utils_albumShare.exportRecipeShare({
              name: recipe.value.name,
              mood: mood.value,
              reason: healingText.value,
              cookingTime: recipe.value.cookingTime,
              difficulty: recipe.value.difficulty,
              ingredients: ingredients.value,
              steps: steps.value,
              image: recipe.value.image,
              guozaiPath: `${utils_assets.STATIC_BASE_URL}/static/guozai/action_16_chopsticks.png`,
              style,
              source: recipe.value.source
            });
          } catch {
            failed.push(style);
          }
        }
        shareCardPaths.value = cards;
        if (!cards[shareStyle.value])
          shareStyle.value = cards.classic ? "classic" : "guozai";
        if (failed.length)
          shareCardError.value = failed.length === 2 ? "食谱卡生成失败，请重试" : "有一张食谱卡没生成好，可重试一次";
      } catch (e) {
        shareCardError.value = readableError(e, "食谱卡生成失败，请重试");
      } finally {
        shareCardLoading.value = false;
      }
    }
    function selectShareStyle(style) {
      if (shareCardLoading.value || !shareCardPaths.value[style])
        return;
      shareStyle.value = style;
      shareCardSaved.value = false;
    }
    function previewShareCard(style) {
      const cards = ["classic", "guozai"].map((key) => ({ key, path: shareCardPaths.value[key] })).filter((item) => Boolean(item.path));
      if (!cards.length)
        return;
      selectShareStyle(style);
      previewImage({
        images: cards.map((item) => item.path),
        startPosition: Math.max(0, cards.findIndex((item) => item.key === style)),
        closeOnClick: false,
        loop: cards.length > 1
      });
    }
    async function saveRecipeCard() {
      if (!recipe.value || shareCardLoading.value)
        return;
      if (!selectedShareCardPath.value)
        await generateShareCards();
      if (!selectedShareCardPath.value)
        return;
      shareCardLoading.value = true;
      shareCardError.value = "";
      try {
        await utils_albumShare.saveShareImage(selectedShareCardPath.value, `${recipe.value.name}-${shareStyle.value === "guozai" ? "锅仔手账" : "今日食谱"}.png`);
        shareCardSaved.value = true;
        utils_toast.toastSuccess("食谱卡已保存");
      } catch (e) {
        shareCardError.value = readableError(e, "保存失败，请授权后重试");
      } finally {
        shareCardLoading.value = false;
      }
    }
    function shareRecipeLink() {
      common_vendor.index.showShareMenu({ menus: ["shareAppMessage", "shareTimeline"] });
      utils_toast.toast("可以从右上角分享给好友");
    }
    async function openAiPanel() {
      showAiPanel.value = true;
      if (!productsLoaded.value && !productsLoading.value)
        await loadProducts();
    }
    async function loadProducts() {
      productsLoading.value = true;
      productsError.value = "";
      try {
        aiProducts.value = await api_virtualCommerce.fetchVirtualProducts();
        productsLoaded.value = true;
      } catch (e) {
        productsError.value = readableError(e, "权益加载失败，请稍后重试");
      } finally {
        productsLoading.value = false;
      }
    }
    async function requestPersonalMenu() {
      if (deepLoading.value)
        return;
      deepLoading.value = true;
      try {
        const openid = await utils_login.ensureLogin();
        recipe.value = await api_recipes.requestDeepRecipe({ openid, mood: mood.value, ingredients: deepIngredients.value.trim(), maxMinutes: deepMinutes.value.trim(), preference: deepPreference.value.trim() });
        imageFailed.value = false;
        showAiPanel.value = false;
        showSteps.value = false;
        shareCardPaths.value = {};
        shareCardError.value = "";
        shareCardSaved.value = false;
        utils_toast.toastSuccess("锅仔为你做好专属菜单啦");
        void loadFeedbackState();
      } catch (e) {
        const message = readableError(e, "生成失败，请稍后重试");
        utils_toast.toast(message.includes("解锁") ? "先解锁私人菜单，就能按食材定制" : message);
      } finally {
        deepLoading.value = false;
      }
    }
    async function purchase(product) {
      if (purchasingSku.value)
        return;
      purchasingSku.value = product.sku;
      let checkingDelivery = false;
      try {
        const openid = await utils_login.ensureLogin();
        const order = await api_virtualCommerce.createVirtualOrder(openid, product.sku);
        const params = await api_virtualCommerce.getVirtualPaymentParams(openid, order.orderNo);
        await api_virtualCommerce.requestWechatVirtualPayment(params);
        checkingDelivery = true;
        common_vendor.index.showLoading({ title: "锅仔正在确认权益…", mask: true });
        const delivered = await waitForDelivery(order.orderNo);
        utils_toast.toast(delivered ? "权益已到账，可以定制菜单啦" : "支付已完成，权益确认中");
      } catch (e) {
        utils_toast.toastError(e, "暂时无法发起支付");
      } finally {
        if (checkingDelivery)
          common_vendor.index.hideLoading();
        purchasingSku.value = "";
      }
    }
    async function waitForDelivery(orderNo) {
      for (let attempt = 0; attempt < 4; attempt += 1) {
        await new Promise((resolve) => setTimeout(resolve, attempt === 0 ? 900 : 1600));
        if ((await api_virtualCommerce.fetchVirtualOrder(orderNo)).status === "DELIVERED")
          return true;
      }
      return false;
    }
    return (_ctx, _cache) => {
      var _a, _b, _c, _d, _e, _f;
      return common_vendor.e({
        a: common_vendor.o(common_vendor.unref(composables_useNavBar.navBack)),
        b: common_vendor.p({
          title: "锅仔食谱推荐",
          ["left-arrow"]: true,
          ["safe-area-inset-top"]: true,
          ["custom-style"]: "background-color: transparent !important;"
        }),
        c: loading.value
      }, loading.value ? common_vendor.e({
        d: `${common_vendor.unref(utils_assets.STATIC_BASE_URL)}/static/guozai/action_10_thinking.png`,
        e: common_vendor.t(((_a = recommendationJob.value) == null ? void 0 : _a.message) || "正在连接锅仔厨房…"),
        f: (_c = (_b = recommendationJob.value) == null ? void 0 : _b.steps) == null ? void 0 : _c.length
      }, ((_e = (_d = recommendationJob.value) == null ? void 0 : _d.steps) == null ? void 0 : _e.length) ? {
        g: common_vendor.f(recommendationJob.value.steps, (step, k0, i0) => {
          return common_vendor.e({
            a: common_vendor.t(stepSymbol(step)),
            b: common_vendor.t(step.label),
            c: common_vendor.t(stepStatusText(step)),
            d: step.status !== "WAITING"
          }, step.status !== "WAITING" ? {
            e: common_vendor.t(step.message)
          } : {}, {
            f: step.stage,
            g: common_vendor.n(`is-${step.status.toLowerCase()}`)
          });
        })
      } : {}) : error.value ? {
        i: common_vendor.o(loadRecipe),
        j: common_vendor.p({
          text: error.value,
          subtext: "网络恢复后，锅仔会接着为你挑菜"
        })
      } : recipe.value ? common_vendor.e({
        l: common_vendor.t(mood.value),
        m: common_vendor.p({
          name: "share",
          size: 36,
          color: "#EF5A3C"
        }),
        n: common_vendor.o(openShareSheet),
        o: recipe.value.source === "AI"
      }, recipe.value.source === "AI" ? {} : {}, {
        p: common_vendor.t(recipe.value.name),
        q: common_vendor.p({
          name: "clock",
          size: 30,
          color: "#EF5A3C"
        }),
        r: common_vendor.t(recipe.value.cookingTime || "--"),
        s: common_vendor.p({
          name: "flame",
          size: 30,
          color: "#EF5A3C"
        }),
        t: common_vendor.t(recipe.value.difficulty || "家常难度"),
        v: recipeImageAvailable.value
      }, recipeImageAvailable.value ? {
        w: recipe.value.image,
        x: common_vendor.o(($event) => imageFailed.value = true)
      } : {
        y: `${common_vendor.unref(utils_assets.STATIC_BASE_URL)}/static/guozai/action_01_bowl.png`
      }, {
        z: `${common_vendor.unref(utils_assets.STATIC_BASE_URL)}/static/guozai/action_16_chopsticks.png`,
        A: common_vendor.t(healingText.value),
        B: recipe.value.description
      }, recipe.value.description ? {
        C: common_vendor.t(recipe.value.description)
      } : {}, {
        D: toolTrace.value.length
      }, toolTrace.value.length ? common_vendor.e({
        E: common_vendor.t(((_f = recommendationJob.value) == null ? void 0 : _f.usedFallback) ? "锅仔暂时休息，已用本地口味推荐" : `${toolTrace.value.length} 个真实步骤已记录`),
        F: showToolTrace.value ? 1 : "",
        G: showToolTrace.value,
        H: common_vendor.o(($event) => showToolTrace.value = !showToolTrace.value),
        I: showToolTrace.value
      }, showToolTrace.value ? {
        J: common_vendor.f(toolTrace.value, (step, k0, i0) => {
          return {
            a: common_vendor.t(stepSymbol(step)),
            b: common_vendor.t(step.label),
            c: common_vendor.t(stepStatusText(step)),
            d: common_vendor.t(step.message),
            e: step.stage
          };
        })
      } : {}) : {}, {
        K: feedbackAvailable.value
      }, feedbackAvailable.value ? {
        L: common_vendor.p({
          name: "heart",
          size: 30,
          color: "#EF5A3C"
        }),
        M: common_vendor.t(feedbackLoading.value === "LIKE" ? "记住中…" : feedbackState.value.liked ? "已喜欢" : "喜欢"),
        N: feedbackState.value.liked ? 1 : "",
        O: Boolean(feedbackLoading.value) ? 1 : "",
        P: feedbackState.value.liked ? "已喜欢这道菜" : "喜欢这道菜",
        Q: common_vendor.o(($event) => sendFeedback("LIKE")),
        R: common_vendor.p({
          name: "dice",
          size: 30,
          color: "#A1826A"
        }),
        S: common_vendor.t(feedbackLoading.value === "DISLIKE" ? "换菜中…" : "不想吃"),
        T: Boolean(feedbackLoading.value) ? 1 : "",
        U: common_vendor.o(($event) => sendFeedback("DISLIKE")),
        V: common_vendor.p({
          name: "camera",
          size: 30,
          color: "#A1826A"
        }),
        W: Boolean(feedbackLoading.value) ? 1 : "",
        X: common_vendor.o(($event) => sendFeedback("MADE"))
      } : {}, {
        Y: `${common_vendor.unref(utils_assets.STATIC_BASE_URL)}/static/guozai/action_10_thinking.png`,
        Z: common_vendor.o(openAiPanel),
        aa: ingredients.value.length
      }, ingredients.value.length ? {
        ab: common_vendor.f(ingredients.value, (ingredient, index, i0) => {
          return {
            a: common_vendor.t(ingredient),
            b: `${ingredient}-${index}`
          };
        })
      } : {}, {
        ac: steps.value.length
      }, steps.value.length ? {
        ad: common_vendor.t(showSteps.value ? "收起" : `查看 ${steps.value.length} 步`),
        ae: showSteps.value ? "收起完整做法" : "展开完整做法",
        af: common_vendor.o(toggleSteps)
      } : {}, {
        ag: showSteps.value && steps.value.length
      }, showSteps.value && steps.value.length ? {
        ah: common_vendor.f(steps.value, (step, index, i0) => {
          return {
            a: common_vendor.t(Number(index) + 1),
            b: common_vendor.t(step),
            c: index
          };
        })
      } : steps.value.length ? {} : {}, {
        ai: steps.value.length,
        aj: common_vendor.p({
          name: steps.value.length ? "flame" : "camera",
          size: 36,
          color: "#fff"
        }),
        ak: common_vendor.t(primaryText.value),
        al: primaryText.value,
        am: common_vendor.o(handlePrimaryAction),
        an: showShareSheet.value
      }, showShareSheet.value ? common_vendor.e({
        ao: common_vendor.t(shareCardLoading.value ? "锅仔正在一次生成两张卡…" : "成品图、材料和做法都装进卡里，选一张保存就行。"),
        ap: common_vendor.o(($event) => showShareSheet.value = false),
        aq: shareCardPaths.value.classic
      }, shareCardPaths.value.classic ? {
        ar: shareCardPaths.value.classic,
        as: common_vendor.o(($event) => previewShareCard("classic"))
      } : {
        at: common_vendor.t(shareCardLoading.value ? "生成中" : "生成失败")
      }, {
        av: shareStyle.value === "classic" ? 1 : "",
        aw: shareCardLoading.value ? 1 : "",
        ax: common_vendor.o(($event) => selectShareStyle("classic")),
        ay: shareCardPaths.value.guozai
      }, shareCardPaths.value.guozai ? {
        az: shareCardPaths.value.guozai,
        aA: common_vendor.o(($event) => previewShareCard("guozai"))
      } : {
        aB: `${common_vendor.unref(utils_assets.STATIC_BASE_URL)}/static/guozai/action_16_chopsticks.png`,
        aC: common_vendor.t(shareCardLoading.value ? "生成中" : "生成失败")
      }, {
        aD: shareStyle.value === "guozai" ? 1 : "",
        aE: shareCardLoading.value ? 1 : "",
        aF: common_vendor.o(($event) => selectShareStyle("guozai")),
        aG: shareCardError.value
      }, shareCardError.value ? {
        aH: common_vendor.t(shareCardError.value)
      } : {}, {
        aI: common_vendor.t(shareCardLoading.value ? "生成两张卡…" : "重新生成两张"),
        aJ: shareCardLoading.value ? 1 : "",
        aK: common_vendor.o(generateShareCards),
        aL: common_vendor.t(shareCardLoading.value ? "请稍候…" : shareCardSaved.value ? "已保存" : "保存选中卡片"),
        aM: shareCardLoading.value || !selectedShareCardPath.value ? 1 : "",
        aN: common_vendor.o(saveRecipeCard),
        aO: common_vendor.p({
          name: "share",
          size: 30,
          color: "#EF5A3C"
        }),
        aP: common_vendor.o(shareRecipeLink),
        aQ: common_vendor.o(() => {
        }),
        aR: common_vendor.o(($event) => showShareSheet.value = false)
      }) : {}, {
        aS: showAiPanel.value
      }, showAiPanel.value ? common_vendor.e({
        aT: common_vendor.o(($event) => showAiPanel.value = false),
        aU: deepIngredients.value,
        aV: common_vendor.o(($event) => deepIngredients.value = $event.detail.value),
        aW: deepMinutes.value,
        aX: common_vendor.o(($event) => deepMinutes.value = $event.detail.value),
        aY: deepPreference.value,
        aZ: common_vendor.o(($event) => deepPreference.value = $event.detail.value),
        ba: common_vendor.t(deepLoading.value ? "锅仔正在组合食材…" : "生成我的专属菜单"),
        bb: deepLoading.value ? 1 : "",
        bc: deepLoading.value ? "专属菜单生成中" : "生成专属菜单",
        bd: common_vendor.o(requestPersonalMenu),
        be: productsLoading.value
      }, productsLoading.value ? {} : productsError.value ? {
        bg: common_vendor.t(productsError.value),
        bh: common_vendor.o(loadProducts)
      } : productsLoaded.value && !aiProducts.value.length ? {} : {}, {
        bf: productsError.value,
        bi: productsLoaded.value && !aiProducts.value.length,
        bj: common_vendor.f(aiProducts.value, (product, k0, i0) => {
          return {
            a: common_vendor.t(product.title),
            b: common_vendor.t(product.description),
            c: common_vendor.t(purchasingSku.value === product.sku ? "处理中…" : `¥${(product.priceFen / 100).toFixed(2)}`),
            d: `购买${product.title}`,
            e: common_vendor.o(($event) => purchase(product), product.sku),
            f: product.sku
          };
        }),
        bk: Boolean(purchasingSku.value) ? 1 : "",
        bl: common_vendor.o(() => {
        })
      }) : {}) : {
        bm: common_vendor.o(loadRecipe),
        bn: common_vendor.p({
          text: "锅仔今天没挑到合适的菜",
          subtext: "换个心情再试一次，或稍后回来看看",
          ["action-text"]: "重新推荐"
        })
      }, {
        h: error.value,
        k: recipe.value
      });
    };
  }
});
_sfc_defineComponent.__runtimeHooks = 6;
const MiniProgramPage = /* @__PURE__ */ common_vendor._export_sfc(_sfc_defineComponent, [["__scopeId", "data-v-2a2b7fc5"]]);
exports.MiniProgramPage = MiniProgramPage;
