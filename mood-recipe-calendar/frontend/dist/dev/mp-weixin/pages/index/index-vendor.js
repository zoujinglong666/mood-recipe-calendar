"use strict";
const common_vendor = require("../../common/vendor.js");
const utils_login = require("../../utils/login.js");
const utils_toast = require("../../utils/toast.js");
const api_share = require("../../api/share.js");
const api_records = require("../../api/records.js");
const api_recipes = require("../../api/recipes.js");
const api_dailyMenu = require("../../api/dailyMenu.js");
const utils_cookingDraft = require("../../utils/cookingDraft.js");
const stores_user = require("../../stores/user.js");
const utils_funnel = require("../../utils/funnel.js");
const utils_assets = require("../../utils/assets.js");
const __unplugin_components_1 = () => "../../node-modules/@wot-ui/ui/components/wd-popup/wd-popup.js";
const __unplugin_components_0 = () => "../../node-modules/@wot-ui/ui/components/wd-navbar/wd-navbar.js";
if (!Array) {
  const _component_wd_navbar = __unplugin_components_0;
  const _component_wd_popup = __unplugin_components_1;
  const _component_layout_tabbar_uni = common_vendor.resolveComponent("layout-tabbar-uni");
  (_component_wd_navbar + _component_wd_popup + _component_layout_tabbar_uni)();
}
if (!Math) {
  (GuozaiWheel + GuozaiNine + GuozaiFlip)();
}
const GuozaiWheel = () => "../../components/guozai/GuozaiWheel.js";
const GuozaiNine = () => "../../components/guozai/GuozaiNine.js";
const GuozaiFlip = () => "../../components/guozai/GuozaiFlip.js";
const ALL_HERO_GUOZAI = [
  { img: utils_assets.STATIC_BASE_URL + "/static/guozai/action_01_bowl.png", name: "端碗锅仔" },
  { img: utils_assets.STATIC_BASE_URL + "/static/guozai/action_02_soup.png", name: "喝汤锅仔" },
  { img: utils_assets.STATIC_BASE_URL + "/static/guozai/action_06_glasses.png", name: "学者锅仔" },
  { img: utils_assets.STATIC_BASE_URL + "/static/guozai/action_07_empty.png", name: "空空锅仔" },
  { img: utils_assets.STATIC_BASE_URL + "/static/guozai/action_08_peek.png", name: "探头锅仔" },
  { img: utils_assets.STATIC_BASE_URL + "/static/guozai/action_09_celebrate.png", name: "庆祝锅仔" },
  { img: utils_assets.STATIC_BASE_URL + "/static/guozai/action_10_thinking.png", name: "思考锅仔" },
  { img: utils_assets.STATIC_BASE_URL + "/static/guozai/action_03_camera.png", name: "相机锅仔" },
  { img: utils_assets.STATIC_BASE_URL + "/static/guozai/action_12_heart.png", name: "比心锅仔" },
  { img: utils_assets.STATIC_BASE_URL + "/static/guozai/action_13_wave.png", name: "挥手锅仔" },
  { img: utils_assets.STATIC_BASE_URL + "/static/guozai/action_14_clap.png", name: "鼓掌锅仔" },
  { img: utils_assets.STATIC_BASE_URL + "/static/guozai/action_15_sleepy.png", name: "困困锅仔" },
  { img: utils_assets.STATIC_BASE_URL + "/static/guozai/action_16_chopsticks.png", name: "干饭锅仔" },
  { img: utils_assets.STATIC_BASE_URL + "/static/guozai/action_17_full.png", name: "饱饱锅仔" },
  { img: utils_assets.STATIC_BASE_URL + "/static/guozai/action_18_cheer.png", name: "加油锅仔" },
  { img: utils_assets.STATIC_BASE_URL + "/static/guozai/action_19_kungfu.png", name: "功夫锅仔" },
  { img: utils_assets.STATIC_BASE_URL + "/static/guozai/action_20_panda.png", name: "熊猫锅仔" }
];
const HERO_GUOZAI_BY_PERIOD = {
  morning: [
    { img: utils_assets.STATIC_BASE_URL + "/static/guozai/action_01_bowl.png", name: "端碗锅仔" },
    { img: utils_assets.STATIC_BASE_URL + "/static/guozai/action_13_wave.png", name: "挥手锅仔" },
    { img: utils_assets.STATIC_BASE_URL + "/static/guozai/action_02_soup.png", name: "端锅锅仔" }
  ],
  noon: [
    { img: utils_assets.STATIC_BASE_URL + "/static/guozai/action_02_soup.png", name: "喝汤锅仔" },
    { img: utils_assets.STATIC_BASE_URL + "/static/guozai/action_16_chopsticks.png", name: "干饭锅仔" },
    { img: utils_assets.STATIC_BASE_URL + "/static/guozai/action_17_full.png", name: "饱饱锅仔" }
  ],
  afternoon: [
    { img: utils_assets.STATIC_BASE_URL + "/static/guozai/action_10_thinking.png", name: "思考锅仔" },
    { img: utils_assets.STATIC_BASE_URL + "/static/guozai/action_14_clap.png", name: "鼓掌锅仔" },
    { img: utils_assets.STATIC_BASE_URL + "/static/guozai/action_12_heart.png", name: "比心锅仔" },
    { img: utils_assets.STATIC_BASE_URL + "/static/guozai/action_20_panda.png", name: "熊猫锅仔" }
  ],
  evening: [
    { img: utils_assets.STATIC_BASE_URL + "/static/guozai/action_09_celebrate.png", name: "庆祝锅仔" },
    { img: utils_assets.STATIC_BASE_URL + "/static/guozai/action_18_cheer.png", name: "加油锅仔" },
    { img: utils_assets.STATIC_BASE_URL + "/static/guozai/action_06_glasses.png", name: "学者锅仔" },
    { img: utils_assets.STATIC_BASE_URL + "/static/guozai/action_19_kungfu.png", name: "功夫锅仔" }
  ],
  late: [
    { img: utils_assets.STATIC_BASE_URL + "/static/guozai/action_08_peek.png", name: "探头锅仔" },
    { img: utils_assets.STATIC_BASE_URL + "/static/guozai/action_15_sleepy.png", name: "困困锅仔" },
    { img: utils_assets.STATIC_BASE_URL + "/static/guozai/action_07_empty.png", name: "空空锅仔" }
  ]
};
const weekCN = ["日", "一", "二", "三", "四", "五", "六"];
function getPeriod(hour) {
  return hour < 5 ? "late" : hour < 11 ? "morning" : hour < 15 ? "noon" : hour < 18 ? "afternoon" : hour < 22 ? "evening" : "late";
}
const _sfc_defineComponent = common_vendor.defineComponent({
  __name: "index",
  setup(__props) {
    const userStore = stores_user.useUserStore();
    const router = common_vendor.useRouter();
    const isBouncing = common_vendor.ref(false);
    function bounceGuozai() {
      isBouncing.value = true;
      setTimeout(() => {
        isBouncing.value = false;
        if (companion.value.actionTarget === "meal-agent") {
          router.push({ name: "meal-agent", query: { prompt: companion.value.actionPrompt || companion.value.actionText } });
          return;
        }
        router.push({ name: "mood" });
      }, 400);
    }
    const now = common_vendor.ref(/* @__PURE__ */ new Date());
    const dateTitle = common_vendor.computed(() => {
      const d = now.value;
      return `${d.getMonth() + 1}月${d.getDate()}日 周${weekCN[d.getDay()]}`;
    });
    const currentMonth = common_vendor.computed(() => `${now.value.getFullYear()}-${String(now.value.getMonth() + 1).padStart(2, "0")}`);
    const initialPeriodPool = HERO_GUOZAI_BY_PERIOD[getPeriod(now.value.getHours())] || HERO_GUOZAI_BY_PERIOD.morning;
    const initialGuozai = initialPeriodPool[now.value.getDate() % initialPeriodPool.length];
    const initialGlobalIndex = Math.max(0, ALL_HERO_GUOZAI.findIndex((g) => g.img === initialGuozai.img));
    const heroGuozaiIndex = common_vendor.ref(initialGlobalIndex);
    const heroGuozai = common_vendor.computed(() => ALL_HERO_GUOZAI[heroGuozaiIndex.value % ALL_HERO_GUOZAI.length]);
    const isHeroCycling = common_vendor.ref(false);
    function cycleHeroGuozai() {
      if (isHeroCycling.value)
        return;
      isHeroCycling.value = true;
      heroGuozaiIndex.value = (heroGuozaiIndex.value + 1) % ALL_HERO_GUOZAI.length;
      setTimeout(() => {
        isHeroCycling.value = false;
      }, 400);
    }
    const monthRecords = common_vendor.ref([]);
    const companion = common_vendor.ref(localCompanion(now.value.getHours()));
    const recipeQuota = common_vendor.ref();
    const recipeQuotaText = common_vendor.computed(() => {
      const quota = recipeQuota.value;
      if (!quota)
        return "抽一道今日治愈菜";
      return quota.member ? `会员今日还可推荐 ${quota.remaining} 次` : `今日还可推荐 ${quota.remaining}/3 次`;
    });
    const monthRecordMap = common_vendor.computed(() => {
      const map = /* @__PURE__ */ new Map();
      monthRecords.value.forEach((record) => {
        var _a;
        const day = Number.parseInt(((_a = record.recordDate) == null ? void 0 : _a.split("-")[2]) || "0", 10);
        if (day > 0) {
          const list = map.get(day);
          if (list)
            list.push(record);
          else
            map.set(day, [record]);
        }
      });
      return map;
    });
    const currentMonthLabel = common_vendor.computed(() => `${now.value.getMonth() + 1}月食光`);
    const latestMonthRecord = common_vendor.computed(() => [...monthRecords.value].sort((a, b) => b.recordDate.localeCompare(a.recordDate))[0]);
    const calendarMemory = common_vendor.computed(() => {
      const record = latestMonthRecord.value;
      if (!record)
        return "这个月的第一顿，锅仔等你留下来。";
      const day = Number.parseInt(record.recordDate.split("-")[2] || "0", 10);
      return `${day}号的${record.dishName}，锅仔还记得。`;
    });
    const calCells = common_vendor.computed(() => {
      const year = now.value.getFullYear();
      const month = now.value.getMonth();
      const firstDay = new Date(year, month, 1).getDay();
      const daysInMonth = new Date(year, month + 1, 0).getDate();
      const cells = [];
      for (let i = 0; i < firstDay; i++)
        cells.push(null);
      for (let d = 1; d <= daysInMonth; d++) {
        const rs = monthRecordMap.value.get(d) || [];
        cells.push({ d, isToday: d === now.value.getDate(), records: rs, count: rs.length });
      }
      while (cells.length % 7 !== 0)
        cells.push(null);
      return cells;
    });
    const loading = common_vendor.ref(false);
    async function loadData() {
      if (loading.value)
        return;
      loading.value = true;
      try {
        await utils_login.ensureLogin();
        monthRecords.value = await api_records.fetchRecordsByMonth(currentMonth.value);
        api_recipes.fetchRecipeQuota().then((q) => {
          recipeQuota.value = q;
        }).catch(() => {
        });
        loadCompanion().catch(() => {
        });
        loadTodayBoard().catch(() => {
        });
        void preloadRecipePool();
      } catch (e) {
        if ((e == null ? void 0 : e.message) !== "NOT_LOGGED_IN")
          utils_toast.toastError(e, "加载失败，请稍后重试");
      } finally {
        loading.value = false;
      }
    }
    function localCompanion(hour) {
      if (hour >= 5 && hour < 11)
        return { greeting: "早上好，先把自己照顾好", message: "早饭不用复杂，热乎顺口就很好。", insight: "锅仔会慢慢记住你的口味", actionText: "告诉我现在的心情" };
      if (hour < 15)
        return { greeting: "到饭点了，别让肚子等太久", message: "忙归忙，午饭还是要认真吃。", insight: "每次选择，都让我更懂你", actionText: "告诉我现在的心情" };
      if (hour < 18)
        return { greeting: "下午好，想想今晚吃什么", message: "提前选好，饿的时候就不用匆忙决定。", insight: "锅仔正在学习你的饭点", actionText: "让锅仔替你挑一道" };
      if (hour < 22)
        return { greeting: "晚上好，今天辛苦啦", message: "先用一顿合胃口的饭，把自己稳稳接住。", insight: "你的口味和忌口，我都会记得", actionText: "告诉我现在的心情" };
      return { greeting: "夜深了，吃点轻松温暖的", message: "选点清淡省事的，吃完也早点休息。", insight: "晚睡的时候，锅仔也在", actionText: "看看适合夜晚的菜" };
    }
    async function loadCompanion() {
      const current = /* @__PURE__ */ new Date();
      const hour = current.getHours();
      const period = hour < 5 ? "late" : hour < 11 ? "morning" : hour < 15 ? "noon" : hour < 18 ? "afternoon" : hour < 22 ? "evening" : "late";
      const cacheKey = `festival-v1-${current.getFullYear()}-${current.getMonth() + 1}-${current.getDate()}-${period}`;
      try {
        const cached = common_vendor.index.getStorageSync("mrc_companion_message");
        if ((cached == null ? void 0 : cached.key) === cacheKey) {
          companion.value = cached.value;
          return;
        }
        const localDate = `${current.getFullYear()}-${String(current.getMonth() + 1).padStart(2, "0")}-${String(current.getDate()).padStart(2, "0")}`;
        const value = await api_records.fetchCompanionMessage(hour, localDate);
        companion.value = value;
        common_vendor.index.setStorageSync("mrc_companion_message", { key: cacheKey, value });
      } catch {
        companion.value = localCompanion(hour);
      }
    }
    const todayBoard = common_vendor.ref();
    async function loadTodayBoard() {
      var _a;
      try {
        todayBoard.value = await api_dailyMenu.fetchDailyBoard();
        if ((_a = todayBoard.value) == null ? void 0 : _a.recipe)
          utils_funnel.funnel("recipe_delivered", { name: todayBoard.value.recipe.name });
        backfillBoardCover();
      } catch {
        todayBoard.value = void 0;
      }
    }
    async function backfillBoardCover() {
      var _a, _b;
      if (!((_a = todayBoard.value) == null ? void 0 : _a.recipe) || todayBoard.value.recipe.image)
        return;
      try {
        const board = await api_dailyMenu.generateDailyMenuCover();
        if ((_b = board == null ? void 0 : board.recipe) == null ? void 0 : _b.image)
          todayBoard.value = board;
      } catch {
      }
    }
    const boardNameParts = common_vendor.computed(() => {
      var _a, _b;
      return api_dailyMenu.splitComboDishName(((_b = (_a = todayBoard.value) == null ? void 0 : _a.recipe) == null ? void 0 : _b.name) || "");
    });
    const recipePoolCache = common_vendor.ref([]);
    const recipePoolAt = common_vendor.ref(0);
    const RECIPE_POOL_TTL = 5 * 60 * 1e3;
    let poolPreloading = false;
    async function preloadRecipePool(force = false) {
      if (poolPreloading)
        return;
      if (!force && recipePoolCache.value.length && Date.now() - recipePoolAt.value < RECIPE_POOL_TTL)
        return;
      poolPreloading = true;
      try {
        const pool = await api_recipes.fetchRecipePool();
        recipePoolCache.value = pool;
        recipePoolAt.value = Date.now();
        pool.slice(0, 24).forEach((item) => {
          if (!item.image)
            return;
          common_vendor.index.getImageInfo({ src: item.image, fail: () => {
          } });
        });
      } catch {
      } finally {
        poolPreloading = false;
      }
    }
    function recipePool() {
      if (recipePoolCache.value.length && Date.now() - recipePoolAt.value < RECIPE_POOL_TTL)
        return Promise.resolve(recipePoolCache.value);
      return preloadRecipePool().then(() => recipePoolCache.value);
    }
    function sampleDiverseDishes(pool, count, excludeName = "") {
      const seen = /* @__PURE__ */ new Set();
      const unique = [];
      for (const item of pool) {
        if (!(item == null ? void 0 : item.name) || !item.image || item.name === excludeName || seen.has(item.name))
          continue;
        seen.add(item.name);
        unique.push(item);
      }
      const shuffled = [...unique].sort(() => Math.random() - 0.5);
      const picked = [];
      const prefixCount = /* @__PURE__ */ new Map();
      for (const item of shuffled) {
        const key = item.name.slice(0, 2);
        const used = prefixCount.get(key) || 0;
        if (used >= 2)
          continue;
        prefixCount.set(key, used + 1);
        picked.push(item);
        if (picked.length >= count)
          break;
      }
      for (const item of shuffled) {
        if (picked.length >= count)
          break;
        if (!picked.includes(item))
          picked.push(item);
      }
      return picked;
    }
    const showWheel = common_vendor.ref(false);
    const wheelPool = common_vendor.ref([]);
    const wheelRef = common_vendor.ref();
    async function openWheel() {
      var _a;
      showWheel.value = true;
      try {
        const pool = await recipePool();
        const current = (_a = todayBoard.value) == null ? void 0 : _a.recipe;
        const others = sampleDiverseDishes(pool, 7, current == null ? void 0 : current.name);
        wheelPool.value = [
          ...(current == null ? void 0 : current.name) ? [{ name: current.name, image: current.image }] : [],
          ...others.map((item) => ({ name: item.name, image: item.image }))
        ];
      } catch {
        wheelPool.value = [];
      }
    }
    async function onWheelSpin() {
      var _a, _b;
      try {
        const board = await api_dailyMenu.refreshDailyBoard();
        todayBoard.value = board;
        (_a = wheelRef.value) == null ? void 0 : _a.land(board.recipe.name, board.recipe.image);
      } catch (e) {
        utils_toast.toastError(e, "今天没有别的菜可换啦");
        (_b = wheelRef.value) == null ? void 0 : _b.fail();
      }
    }
    const showNine = common_vendor.ref(false);
    const nineItems = common_vendor.ref([]);
    const nineRecipes = common_vendor.ref([]);
    const ninePick = common_vendor.ref();
    const nineRef = common_vendor.ref();
    async function openNine() {
      showNine.value = true;
      try {
        const pool = await recipePool();
        const sampled = sampleDiverseDishes(pool, 8);
        nineRecipes.value = sampled;
        nineItems.value = sampled.map((item) => ({ name: item.name, image: item.image }));
      } catch {
        nineRecipes.value = [];
        nineItems.value = [];
      }
    }
    function onNineStart() {
      var _a;
      if (!nineRecipes.value.length)
        return;
      const index = Math.floor(Math.random() * nineRecipes.value.length);
      ninePick.value = nineRecipes.value[index];
      (_a = nineRef.value) == null ? void 0 : _a.land(index);
    }
    function onNineCook(index) {
      const recipe = ninePick.value && nineRecipes.value[index] === ninePick.value ? ninePick.value : nineRecipes.value[index];
      if (!recipe) {
        utils_toast.toastError(new Error("这一格是锅仔的彩蛋，再翻一次吧"), "翻到装饰格了");
        return;
      }
      showNine.value = false;
      utils_cookingDraft.saveCookingDraft(recipe, "满足");
      router.push({ name: "cooking" });
    }
    const showFlip = common_vendor.ref(false);
    const flipItems = common_vendor.ref([]);
    const flipPool = common_vendor.ref([]);
    const flipPick = common_vendor.ref();
    const flipRef = common_vendor.ref();
    async function openFlip() {
      showFlip.value = true;
      try {
        const pool = await recipePool();
        flipPool.value = pool;
        flipItems.value = flipBoardItems(pool);
      } catch {
        flipPool.value = [];
        flipItems.value = [];
      }
    }
    function flipBoardItems(pool) {
      const sampled = sampleDiverseDishes(pool, 8);
      const items = sampled.map((item) => ({ name: item.name, image: item.image }));
      for (const item of pool) {
        if (items.length >= 8)
          break;
        if ((item == null ? void 0 : item.name) && !items.some((f) => f.name === item.name))
          items.push({ name: item.name, image: item.image });
      }
      return items;
    }
    async function onFlipStart() {
      var _a, _b, _c;
      if (!flipPool.value.length) {
        try {
          flipPool.value = await recipePool();
        } catch {
          flipPool.value = [];
        }
        if (!flipPool.value.length) {
          utils_toast.toastError(new Error("菜谱还没备好，稍后再来试试"), "翻一翻");
          (_a = flipRef.value) == null ? void 0 : _a.fail();
          return;
        }
        flipItems.value = flipBoardItems(flipPool.value);
      }
      const winners = sampleDiverseDishes(flipPool.value, 1);
      const pick = winners[0] || flipPool.value.find((item) => (item == null ? void 0 : item.name) && item.name !== "锅仔牌");
      if (!pick) {
        utils_toast.toastError(new Error("菜谱还没备好，稍后再来试试"), "翻一翻");
        (_b = flipRef.value) == null ? void 0 : _b.fail();
        return;
      }
      const others = sampleDiverseDishes(flipPool.value, 7, pick.name);
      flipPick.value = pick;
      flipItems.value = [
        ...others.map((item) => ({ name: item.name, image: item.image })),
        { name: "锅仔牌" }
      ];
      (_c = flipRef.value) == null ? void 0 : _c.deal({ name: pick.name, image: pick.image });
    }
    function onFlipCook() {
      const recipe = flipPick.value;
      if (!recipe)
        return;
      showFlip.value = false;
      utils_cookingDraft.saveCookingDraft(recipe, "满足");
      router.push({ name: "cooking" });
    }
    function goCookToday() {
      var _a;
      const board = todayBoard.value;
      if (!((_a = board == null ? void 0 : board.recipe) == null ? void 0 : _a.id))
        return;
      utils_cookingDraft.saveCookingDraft(board.recipe, "满足");
      router.push({ name: "cooking" });
    }
    const friendDish = common_vendor.ref("");
    const inviteBonus = common_vendor.ref(false);
    common_vendor.onLoad((query) => {
      const sharer = String((query == null ? void 0 : query.sharer) || "");
      const dish = String((query == null ? void 0 : query.dish) || "");
      if (dish)
        friendDish.value = dish;
      if (!sharer || sharer === userStore.openid)
        return;
      api_share.acceptShareInvite(sharer).then((res) => {
        if (res == null ? void 0 : res.grantedBonus) {
          inviteBonus.value = true;
          utils_toast.toastSuccess("好友分享的福利：今日对话 +1 次");
        }
      }).catch(() => {
      });
    });
    const FAV_TIP_KEY = "mrc_fav_tip_dismissed";
    const FAV_TIP_COUNT_KEY = "mrc_fav_tip_shown_count";
    const showFavTip = common_vendor.ref(false);
    function maybeShowFavTip() {
      if (common_vendor.index.getStorageSync(FAV_TIP_KEY))
        return;
      const n = Number(common_vendor.index.getStorageSync(FAV_TIP_COUNT_KEY) || 0);
      if (n >= 3)
        return;
      common_vendor.index.setStorageSync(FAV_TIP_COUNT_KEY, n + 1);
      showFavTip.value = true;
    }
    function dismissFavTip() {
      showFavTip.value = false;
      common_vendor.index.setStorageSync(FAV_TIP_KEY, "1");
    }
    common_vendor.onShow(() => {
      now.value = /* @__PURE__ */ new Date();
      loadData();
      maybeShowFavTip();
      utils_funnel.funnel("home_view");
    });
    common_vendor.onShareAppMessage(() => {
      var _a, _b;
      const sharer = userStore.openid;
      const dish = ((_b = (_a = todayBoard.value) == null ? void 0 : _a.recipe) == null ? void 0 : _b.name) || "";
      const params = [
        sharer ? `sharer=${encodeURIComponent(sharer)}` : "",
        dish ? `dish=${encodeURIComponent(dish)}` : ""
      ].filter(Boolean).join("&");
      const path = `/pages/index/index${params ? `?${params}` : ""}`;
      const title = dish ? `我用锅仔安排了「${dish}」，也替你留了一份` : "锅仔 · 按心情帮你决定今天吃什么";
      void api_share.recordShare("home").catch(() => {
      });
      return { title, path };
    });
    common_vendor.onShareTimeline(() => {
      const sharer = userStore.openid;
      return {
        title: "锅仔 · 按心情帮你决定今天吃什么",
        query: sharer ? `sharer=${encodeURIComponent(sharer)}` : ""
      };
    });
    const MOODS = ["开心", "平静", "疲惫", "焦虑", "难过", "嘴馋", "低落", "想家", "期待", "满足", "得意", "害羞"];
    const showMealSheet = common_vendor.ref(false);
    function openMealSheet() {
      showMealSheet.value = true;
    }
    function pickMeal(type) {
      showMealSheet.value = false;
      utils_funnel.funnel("recommend_click", { mealType: type });
      const mood = MOODS[Math.floor(Math.random() * MOODS.length)];
      router.push({ name: "recipe", params: { mood }, query: { mood, mealType: type } });
    }
    const TABBAR_NAMES = ["home", "record", "profile"];
    function goto(name, q) {
      if (TABBAR_NAMES.includes(name))
        router.pushTab({ name });
      else
        router.push({ name, query: {} });
    }
    function openCalendarCell(cell) {
      if (cell.records.length)
        router.push({ name: "calendar", params: { day: String(cell.d) } });
      else
        router.pushTab({ name: "record" });
    }
    return (_ctx, _cache) => {
      return common_vendor.e({
        a: common_vendor.p({
          title: "心情菜谱日历",
          ["safe-area-inset-top"]: true,
          ["custom-style"]: "background-color: transparent !important;"
        }),
        b: common_vendor.t(dateTitle.value),
        c: common_vendor.t(companion.value.greeting),
        d: common_vendor.t(companion.value.message),
        e: common_vendor.t(heroGuozai.value.name),
        f: common_vendor.t(companion.value.insight),
        g: !isBouncing.value && !isHeroCycling.value ? 1 : "",
        h: isBouncing.value ? 1 : "",
        i: isHeroCycling.value ? 1 : "",
        j: heroGuozai.value.img,
        k: heroGuozai.value.img,
        l: common_vendor.o(cycleHeroGuozai),
        m: common_vendor.unref(utils_assets.STATIC_BASE_URL) + "/static/guozai/mood_01_happy.png",
        n: common_vendor.o(($event) => goto("profile")),
        o: common_vendor.t(companion.value.actionText),
        p: companion.value.scene && companion.value.scene !== "DAILY" ? 1 : "",
        q: companion.value.actionText,
        r: common_vendor.o(bounceGuozai),
        s: friendDish.value || inviteBonus.value
      }, friendDish.value || inviteBonus.value ? common_vendor.e({
        t: friendDish.value
      }, friendDish.value ? {
        v: common_vendor.t(friendDish.value)
      } : {}, {
        w: inviteBonus.value
      }, inviteBonus.value ? {} : {}) : {}, {
        x: todayBoard.value
      }, todayBoard.value ? common_vendor.e({
        y: common_vendor.t(boardNameParts.value.main),
        z: boardNameParts.value.drink
      }, boardNameParts.value.drink ? {
        A: common_vendor.t(boardNameParts.value.drink)
      } : {}, {
        B: todayBoard.value.recipe.cookingTime
      }, todayBoard.value.recipe.cookingTime ? {
        C: common_vendor.t(todayBoard.value.recipe.cookingTime)
      } : {}, {
        D: todayBoard.value.recipe.difficulty
      }, todayBoard.value.recipe.difficulty ? {
        E: common_vendor.t(todayBoard.value.recipe.difficulty)
      } : {}, {
        F: common_vendor.o(goCookToday),
        G: todayBoard.value.recipe.image
      }, todayBoard.value.recipe.image ? {
        H: todayBoard.value.recipe.image
      } : {
        I: common_vendor.unref(utils_assets.STATIC_BASE_URL) + "/static/guozai/action_01_bowl.png"
      }, {
        J: `锅仔今天留了${todayBoard.value.recipe.name}，打开看做法`,
        K: common_vendor.o(goCookToday)
      }) : {}, {
        L: showFavTip.value
      }, showFavTip.value ? {
        M: common_vendor.o(dismissFavTip)
      } : {}, {
        N: common_vendor.t(recipeQuotaText.value),
        O: common_vendor.unref(utils_assets.STATIC_BASE_URL) + "/static/guozai/action_10_thinking.png",
        P: common_vendor.o(openMealSheet),
        Q: common_vendor.unref(utils_assets.STATIC_BASE_URL) + "/static/guozai/action_08_peek.png",
        R: common_vendor.o(openWheel),
        S: common_vendor.unref(utils_assets.STATIC_BASE_URL) + "/static/guozai/action_09_celebrate.png",
        T: common_vendor.o(openNine),
        U: common_vendor.unref(utils_assets.STATIC_BASE_URL) + "/static/guozai/action_02_soup.png",
        V: common_vendor.o(openFlip),
        W: common_vendor.unref(utils_assets.STATIC_BASE_URL) + "/static/guozai/action_06_glasses.png",
        X: common_vendor.o(($event) => goto("meal-agent")),
        Y: common_vendor.unref(utils_assets.STATIC_BASE_URL) + "/static/guozai/action_06_glasses.png",
        Z: common_vendor.o(($event) => goto("recipe-library")),
        aa: common_vendor.unref(utils_assets.STATIC_BASE_URL) + "/static/guozai/action_01_bowl.png",
        ab: common_vendor.o(($event) => goto("daily-meal-plan")),
        ac: common_vendor.o(($event) => pickMeal("BREAKFAST")),
        ad: common_vendor.o(($event) => pickMeal("LUNCH")),
        ae: common_vendor.o(($event) => pickMeal("DINNER")),
        af: common_vendor.o(($event) => showMealSheet.value = false),
        ag: common_vendor.o(($event) => showMealSheet.value = $event),
        ah: common_vendor.p({
          position: "bottom",
          ["safe-area-inset-bottom"]: true,
          ["custom-style"]: "border-radius: 40rpx 40rpx 0 0; overflow: hidden; background: var(--mrc-surface);",
          modelValue: showMealSheet.value
        }),
        ai: common_vendor.t(currentMonthLabel.value),
        aj: common_vendor.t(monthRecordMap.value.size),
        ak: common_vendor.unref(utils_assets.STATIC_BASE_URL) + "/static/guozai/action_08_peek.png",
        al: common_vendor.f(weekCN, (w, k0, i0) => {
          return {
            a: common_vendor.t(w),
            b: w
          };
        }),
        am: common_vendor.f(calCells.value, (c, i, i0) => {
          var _a, _b;
          return common_vendor.e({
            a: c
          }, c ? common_vendor.e({
            b: (_a = c.records[0]) == null ? void 0 : _a.imageUrl
          }, ((_b = c.records[0]) == null ? void 0 : _b.imageUrl) ? {
            c: c.records[0].imageUrl
          } : {}, {
            d: common_vendor.t(c.d),
            e: c.records.length && !c.records[0].imageUrl
          }, c.records.length && !c.records[0].imageUrl ? {} : {}, {
            f: c.count > 1
          }, c.count > 1 ? {
            g: common_vendor.t(c.count)
          } : {}, {
            h: c.isToday ? 1 : "",
            i: c.records.length ? 1 : "",
            j: c.records.length ? `${c.d}日，${c.records[0].dishName}${c.records.length > 1 ? `等${c.records.length}餐` : ""}，打开记录` : `${c.d}日，记录一餐`,
            k: common_vendor.o(($event) => openCalendarCell(c), i)
          }) : {}, {
            l: i
          });
        }),
        an: common_vendor.unref(utils_assets.STATIC_BASE_URL) + "/static/guozai/action_12_heart.png",
        ao: common_vendor.t(calendarMemory.value),
        ap: common_vendor.o(($event) => goto("calendar")),
        aq: common_vendor.sr(wheelRef, "83a5a03c-3,83a5a03c-0", {
          "k": "wheelRef"
        }),
        ar: common_vendor.o(onWheelSpin),
        as: common_vendor.o(($event) => showWheel.value = false),
        at: common_vendor.p({
          visible: showWheel.value,
          items: wheelPool.value
        }),
        av: common_vendor.sr(nineRef, "83a5a03c-4,83a5a03c-0", {
          "k": "nineRef"
        }),
        aw: common_vendor.o(onNineStart),
        ax: common_vendor.o(onNineCook),
        ay: common_vendor.o(($event) => showNine.value = false),
        az: common_vendor.p({
          visible: showNine.value,
          items: nineItems.value
        }),
        aA: common_vendor.sr(flipRef, "83a5a03c-5,83a5a03c-0", {
          "k": "flipRef"
        }),
        aB: common_vendor.o(onFlipStart),
        aC: common_vendor.o(onFlipCook),
        aD: common_vendor.o(($event) => showFlip.value = false),
        aE: common_vendor.p({
          visible: showFlip.value,
          items: flipItems.value
        })
      });
    };
  }
});
_sfc_defineComponent.__runtimeHooks = 6;
const MiniProgramPage = /* @__PURE__ */ common_vendor._export_sfc(_sfc_defineComponent, [["__scopeId", "data-v-83a5a03c"]]);
exports.MiniProgramPage = MiniProgramPage;
