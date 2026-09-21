"use strict";
const common_vendor = require("../../common/vendor.js");
const utils_assets = require("../../utils/assets.js");
const utils_login = require("../../utils/login.js");
const utils_toast = require("../../utils/toast.js");
const api_records = require("../../api/records.js");
const __unplugin_components_0 = () => "../../node-modules/@wot-ui/ui/components/wd-navbar/wd-navbar.js";
if (!Array) {
  const _component_wd_navbar = __unplugin_components_0;
  const _component_layout_tabbar_uni = common_vendor.resolveComponent("layout-tabbar-uni");
  (_component_wd_navbar + _component_layout_tabbar_uni)();
}
const _sfc_main = /* @__PURE__ */ common_vendor.defineComponent({
  __name: "index",
  setup(__props) {
    const router = common_vendor.useRouter();
    const isBouncing = common_vendor.ref(false);
    function bounceGuozai() {
      isBouncing.value = true;
      setTimeout(() => {
        isBouncing.value = false;
        router.push({ name: "mood" });
      }, 400);
    }
    const now = /* @__PURE__ */ new Date();
    const weekCN = ["日", "一", "二", "三", "四", "五", "六"];
    const dateTitle = `${now.getMonth() + 1}月${now.getDate()}日 周${weekCN[now.getDay()]}`;
    const currentMonth = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, "0")}`;
    const ALL_HERO_GUOZAI = [
      { img: utils_assets.STATIC_BASE_URL + "/static/guozai/action_01_bowl.png", name: "端碗锅仔" },
      { img: utils_assets.STATIC_BASE_URL + "/static/guozai/action_02_soup.png", name: "喝汤锅仔" },
      { img: utils_assets.STATIC_BASE_URL + "/static/guozai/action_06_glasses.png", name: "学者锅仔" },
      { img: utils_assets.STATIC_BASE_URL + "/static/guozai/action_07_empty.png", name: "空空锅仔" },
      { img: utils_assets.STATIC_BASE_URL + "/static/guozai/action_08_peek.png", name: "探头锅仔" },
      { img: utils_assets.STATIC_BASE_URL + "/static/guozai/action_09_celebrate.png", name: "庆祝锅仔" },
      { img: utils_assets.STATIC_BASE_URL + "/static/guozai/action_10_thinking.png", name: "思考锅仔" },
      { img: utils_assets.STATIC_BASE_URL + "/static/guozai/action_11_cooking.png", name: "厨师锅仔" },
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
        { img: utils_assets.STATIC_BASE_URL + "/static/guozai/action_11_cooking.png", name: "厨师锅仔" }
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
    function getPeriod(hour) {
      return hour < 5 ? "late" : hour < 11 ? "morning" : hour < 15 ? "noon" : hour < 18 ? "afternoon" : hour < 22 ? "evening" : "late";
    }
    const initialPeriodPool = HERO_GUOZAI_BY_PERIOD[getPeriod(now.getHours())] || HERO_GUOZAI_BY_PERIOD.morning;
    const initialGuozai = initialPeriodPool[now.getDate() % initialPeriodPool.length];
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
    const companion = common_vendor.ref(localCompanion(now.getHours()));
    const monthRecordMap = common_vendor.computed(() => {
      const map = /* @__PURE__ */ new Map();
      monthRecords.value.forEach((record) => {
        var _a;
        const day = Number.parseInt(((_a = record.recordDate) == null ? void 0 : _a.split("-")[2]) || "0", 10);
        if (day > 0)
          map.set(day, record);
      });
      return map;
    });
    const currentMonthLabel = `${now.getMonth() + 1}月食光`;
    const latestMonthRecord = common_vendor.computed(() => [...monthRecords.value].sort((a, b) => b.recordDate.localeCompare(a.recordDate))[0]);
    const calendarMemory = common_vendor.computed(() => {
      const record = latestMonthRecord.value;
      if (!record)
        return "这个月的第一顿，锅仔等你留下来。";
      const day = Number.parseInt(record.recordDate.split("-")[2] || "0", 10);
      return `${day}号的${record.dishName}，锅仔还记得。`;
    });
    const calCells = common_vendor.computed(() => {
      const year = now.getFullYear();
      const month = now.getMonth();
      const firstDay = new Date(year, month, 1).getDay();
      const daysInMonth = new Date(year, month + 1, 0).getDate();
      const cells = [];
      for (let i = 0; i < firstDay; i++)
        cells.push(null);
      for (let d = 1; d <= daysInMonth; d++)
        cells.push({ d, isToday: d === now.getDate(), record: monthRecordMap.value.get(d) });
      while (cells.length % 7 !== 0)
        cells.push(null);
      return cells;
    });
    async function loadData() {
      try {
        const openid = await utils_login.ensureLogin();
        const [records] = await Promise.all([api_records.fetchRecordsByMonth(openid, currentMonth), loadCompanion()]);
        monthRecords.value = records;
      } catch (e) {
        if ((e == null ? void 0 : e.message) !== "NOT_LOGGED_IN")
          utils_toast.toastError(e, "加载失败，请稍后重试");
      }
    }
    function localCompanion(hour) {
      if (hour >= 5 && hour < 11)
        return { greeting: "早上好，先把自己照顾好", message: "早饭不用复杂，热乎顺口就很好。", insight: "锅仔会慢慢记住你的口味", actionText: "告诉我现在的心情" };
      if (hour < 15)
        return { greeting: "到饭点了，别让肚子等太久", message: "忙归忙，午饭还是要认真吃。", insight: "每次选择，都让我更懂你", actionText: "告诉我现在的心情" };
      if (hour < 18)
        return { greeting: "下午好，想想今晚吃什么", message: "提前选好，饿的时候就不用匆忙决定。", insight: "锅仔正在学习你的饭点", actionText: "看看今天吃什么" };
      if (hour < 22)
        return { greeting: "晚上好，今天辛苦啦", message: "先用一顿合胃口的饭，把自己稳稳接住。", insight: "你的口味和忌口，我都会记得", actionText: "告诉我现在的心情" };
      return { greeting: "夜深了，吃点轻松温暖的", message: "选点清淡省事的，吃完也早点休息。", insight: "晚睡的时候，锅仔也在", actionText: "看看适合夜晚的菜" };
    }
    async function loadCompanion() {
      const current = /* @__PURE__ */ new Date();
      const hour = current.getHours();
      const period = hour < 5 ? "late" : hour < 11 ? "morning" : hour < 15 ? "noon" : hour < 18 ? "afternoon" : hour < 22 ? "evening" : "late";
      const cacheKey = `${current.getFullYear()}-${current.getMonth() + 1}-${current.getDate()}-${period}`;
      try {
        const cached = common_vendor.index.getStorageSync("mrc_companion_message");
        if ((cached == null ? void 0 : cached.key) === cacheKey) {
          companion.value = cached.value;
          return;
        }
        const value = await api_records.fetchCompanionMessage(hour);
        companion.value = value;
        common_vendor.index.setStorageSync("mrc_companion_message", { key: cacheKey, value });
      } catch {
        companion.value = localCompanion(hour);
      }
    }
    common_vendor.onShow(loadData);
    const MOODS = ["开心", "平静", "疲惫", "焦虑", "难过", "嘴馋", "低落", "想家", "期待", "满足", "得意", "害羞"];
    function gotoLucky() {
      router.push({ name: "recipe", query: { mood: MOODS[Math.floor(Math.random() * MOODS.length)], random: "1" } });
    }
    function goto(name, q) {
      router.push({ name, query: {} });
    }
    function openCalendarCell(cell) {
      if (cell.record)
        router.push({ name: "calendar", query: { day: String(cell.d) } });
      else
        router.pushTab({ name: "record" });
    }
    return (_ctx, _cache) => {
      return {
        a: common_vendor.p({
          title: "心情菜谱日历",
          ["safe-area-inset-top"]: true,
          ["custom-style"]: "background-color: transparent !important;"
        }),
        b: common_vendor.t(dateTitle),
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
        p: common_vendor.o(bounceGuozai),
        q: common_vendor.unref(utils_assets.STATIC_BASE_URL) + "/static/guozai/action_10_thinking.png",
        r: common_vendor.o(gotoLucky),
        s: common_vendor.unref(utils_assets.STATIC_BASE_URL) + "/static/guozai/action_02_soup.png",
        t: common_vendor.o(gotoLucky),
        v: common_vendor.unref(utils_assets.STATIC_BASE_URL) + "/static/guozai/action_03_camera.png",
        w: common_vendor.o(($event) => common_vendor.unref(router).pushTab({
          name: "record"
        })),
        x: common_vendor.t(currentMonthLabel),
        y: common_vendor.t(monthRecordMap.value.size),
        z: common_vendor.unref(utils_assets.STATIC_BASE_URL) + "/static/guozai/action_08_peek.png",
        A: common_vendor.f(weekCN, (w, k0, i0) => {
          return {
            a: common_vendor.t(w),
            b: w
          };
        }),
        B: common_vendor.f(calCells.value, (c, i, i0) => {
          var _a, _b;
          return common_vendor.e({
            a: c
          }, c ? common_vendor.e({
            b: (_a = c.record) == null ? void 0 : _a.imageUrl
          }, ((_b = c.record) == null ? void 0 : _b.imageUrl) ? {
            c: c.record.imageUrl
          } : {}, {
            d: common_vendor.t(c.d),
            e: c.record && !c.record.imageUrl
          }, c.record && !c.record.imageUrl ? {} : {}, {
            f: c.isToday ? 1 : "",
            g: c.record ? 1 : "",
            h: c.record ? `${c.d}日，${c.record.dishName}，打开记录` : `${c.d}日，记录一餐`,
            i: common_vendor.o(($event) => openCalendarCell(c), i)
          }) : {}, {
            j: i
          });
        }),
        C: common_vendor.unref(utils_assets.STATIC_BASE_URL) + "/static/guozai/action_12_heart.png",
        D: common_vendor.t(calendarMemory.value),
        E: common_vendor.o(($event) => goto("calendar"))
      };
    };
  }
});
const MiniProgramPage = /* @__PURE__ */ common_vendor._export_sfc(_sfc_main, [["__scopeId", "data-v-83a5a03c"]]);
exports.MiniProgramPage = MiniProgramPage;
