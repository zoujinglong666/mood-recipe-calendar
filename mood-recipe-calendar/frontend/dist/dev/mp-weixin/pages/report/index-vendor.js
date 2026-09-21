"use strict";
const common_vendor = require("../../common/vendor.js");
const composables_useNavBar = require("../../composables/useNavBar.js");
const utils_assets = require("../../utils/assets.js");
const utils_login = require("../../utils/login.js");
const api_records = require("../../api/records.js");
const __unplugin_components_0 = () => "../../node-modules/@wot-ui/ui/components/wd-navbar/wd-navbar.js";
if (!Array) {
  const _component_wd_navbar = __unplugin_components_0;
  const _component_layout_default_uni = common_vendor.resolveComponent("layout-default-uni");
  (_component_wd_navbar + _component_layout_default_uni)();
}
if (!Math) {
  (Icon + LoadingState + ErrorState)();
}
const Icon = () => "../../components/common/Icon.js";
const LoadingState = () => "../../components/guozai/LoadingState.js";
const ErrorState = () => "../../components/guozai/ErrorState.js";
const totalPages = 8;
const _sfc_main = /* @__PURE__ */ common_vendor.defineComponent({
  __name: "index",
  setup(__props) {
    const toast = common_vendor.useToast();
    const currentPage = common_vendor.ref(0);
    const loading = common_vendor.ref(true);
    const error = common_vendor.ref("");
    const yearStats = common_vendor.ref(null);
    const currentYear = (/* @__PURE__ */ new Date()).getFullYear();
    const moodList = common_vendor.computed(() => {
      var _a;
      const dist = ((_a = yearStats.value) == null ? void 0 : _a.moodDistribution) || {};
      return Object.entries(dist).map(([mood, count]) => ({ mood, count }));
    });
    const monthlyData = common_vendor.computed(() => {
      var _a;
      const heat = ((_a = yearStats.value) == null ? void 0 : _a.monthlyHeatmap) || {};
      return Array.from({ length: 12 }, (_, i) => ({
        month: i + 1,
        count: heat[`${currentYear}-${String(i + 1).padStart(2, "0")}`] || 0
      }));
    });
    const maxMonthly = common_vendor.computed(() => Math.max(...monthlyData.value.map((m) => m.count), 1));
    const avgFreq = common_vendor.computed(() => {
      var _a, _b;
      const days = ((_a = yearStats.value) == null ? void 0 : _a.totalDays) || 0;
      const recs = ((_b = yearStats.value) == null ? void 0 : _b.totalRecords) || 0;
      if (!days)
        return "0.0";
      return (recs / days).toFixed(1);
    });
    const top3 = common_vendor.computed(() => {
      var _a;
      return (((_a = yearStats.value) == null ? void 0 : _a.topDishes) || []).slice(0, 3);
    });
    const peakMonth = common_vendor.computed(() => {
      let idx = -1;
      let cnt = 0;
      monthlyData.value.forEach((m, i) => {
        if (m.count > cnt) {
          cnt = m.count;
          idx = i;
        }
      });
      return { month: idx + 1, count: cnt };
    });
    const happyDays = common_vendor.computed(() => {
      var _a, _b;
      return ((_b = (_a = yearStats.value) == null ? void 0 : _a.moodDistribution) == null ? void 0 : _b["开心"]) || 0;
    });
    const messageLines = common_vendor.computed(() => {
      var _a, _b, _c, _d;
      const recs = ((_a = yearStats.value) == null ? void 0 : _a.totalRecords) || 0;
      const days = ((_b = yearStats.value) == null ? void 0 : _b.totalDays) || 0;
      const topName = ((_c = top3.value[0]) == null ? void 0 : _c.name) || "美食";
      const top2Name = ((_d = top3.value[1]) == null ? void 0 : _d.name) || "家常菜";
      return [
        `这一年你做了${recs}道菜，`,
        `记录了${days}天。开心的时候你`,
        `奖励自己大餐，疲惫的时候你`,
        `用热汤取暖。${topName}是你的`,
        `本命，${top2Name}是你的安慰。`,
        `${currentYear}辛苦了，${currentYear + 1}也要继续`,
        `好好吃饭呀。`
      ];
    });
    const TOP3_AI = ["你的本命菜，怎么做都不腻", "简单却永远吃不腻的国民菜", "下饭神器，你一定很爱米饭"];
    function top3Ai(idx) {
      return TOP3_AI[idx] || "这道菜陪伴了你很多个日子";
    }
    const TOP3_BGS = ["linear-gradient(135deg,#FFB088,#FF8C66)", "linear-gradient(135deg,#FFD180,#FFA726)", "linear-gradient(135deg,#A5D6A7,#66BB6A)"];
    function top3Bg(idx) {
      return TOP3_BGS[idx] || "linear-gradient(135deg,#CE93D8,#AB47BC)";
    }
    const BAR_PALETTE = ["#FDE0C8", "#FCD4B0", "#FBC898", "#FABC80", "#F9B068", "#F8A450", "#F59848", "#F08C40", "#EB8038", "#E67430", "#E16828", "#DC5C20"];
    function barColor(i) {
      return BAR_PALETTE[i % BAR_PALETTE.length];
    }
    async function loadStats() {
      loading.value = true;
      error.value = "";
      try {
        const openid = await utils_login.ensureLogin();
        yearStats.value = await api_records.fetchYearStats(openid, currentYear);
      } catch (e) {
        error.value = e.message || "加载失败";
      } finally {
        loading.value = false;
      }
    }
    common_vendor.onShow(() => {
      currentPage.value = 0;
      loadStats();
    });
    function onSwiperChange(e) {
      currentPage.value = e.detail.current;
    }
    function share() {
      toast.show("分享长图即将上线");
    }
    return (_ctx, _cache) => {
      var _a, _b, _c, _d, _e, _f;
      return common_vendor.e({
        a: common_vendor.o(common_vendor.unref(composables_useNavBar.navBack)),
        b: common_vendor.p({
          title: "年度报告",
          ["left-arrow"]: true,
          ["safe-area-inset-top"]: true,
          ["custom-style"]: "background-color: transparent !important;"
        }),
        c: common_vendor.p({
          name: "share",
          size: 36,
          color: "#6A4A37"
        }),
        d: common_vendor.o(share),
        e: loading.value
      }, loading.value ? {
        f: common_vendor.p({
          text: "锅仔正在整理年度报告..."
        })
      } : error.value ? {
        h: common_vendor.o(loadStats)
      } : common_vendor.e({
        i: common_vendor.t(common_vendor.unref(currentYear)),
        j: common_vendor.unref(utils_assets.STATIC_BASE_URL) + "/static/guozai/action_06_glasses.png",
        k: common_vendor.t(common_vendor.unref(currentYear)),
        l: common_vendor.t(((_a = yearStats.value) == null ? void 0 : _a.totalDays) || 0),
        m: common_vendor.t(((_b = yearStats.value) == null ? void 0 : _b.totalRecords) || 0),
        n: common_vendor.t(avgFreq.value),
        o: common_vendor.unref(utils_assets.STATIC_BASE_URL) + "/static/guozai/mood_10_content.png",
        p: common_vendor.t(common_vendor.unref(currentYear)),
        q: !top3.value.length
      }, !top3.value.length ? {} : {}, {
        r: common_vendor.f(top3.value, (d, i, i0) => {
          return {
            a: common_vendor.t(["🥇", "🥈", "🥉"][i]),
            b: common_vendor.n(i === 0 ? "rpt-top3__medal--gold" : i === 1 ? "rpt-top3__medal--silver" : "rpt-top3__medal--bronze"),
            c: common_vendor.t(d.name.charAt(0)),
            d: top3Bg(i),
            e: common_vendor.t(d.name),
            f: common_vendor.t(d.count),
            g: common_vendor.t(top3Ai(i)),
            h: d.name
          };
        }),
        s: common_vendor.unref(utils_assets.STATIC_BASE_URL) + "/static/guozai/mood_11_proud.png",
        t: !moodList.value.length
      }, !moodList.value.length ? {} : {}, {
        v: common_vendor.f(moodList.value, (m, i, i0) => {
          return {
            a: common_vendor.n("rpt-mood__dot--" + (i % 9 + 1)),
            b: common_vendor.t(m.mood),
            c: common_vendor.t(m.count),
            d: m.mood
          };
        }),
        w: common_vendor.t(happyDays.value),
        x: common_vendor.unref(utils_assets.STATIC_BASE_URL) + "/static/guozai/mood_01_happy.png",
        y: common_vendor.t(peakMonth.value.count > 0 ? peakMonth.value.month + "月 " + peakMonth.value.count + "天" : "记录中…"),
        z: common_vendor.f(monthlyData.value, (m, k0, i0) => {
          return {
            a: m.month,
            b: m.count > 0 && m.count === peakMonth.value.count ? 1 : "",
            c: Math.max(m.count / maxMonthly.value * 100, m.count > 0 ? 8 : 2) + "%",
            d: barColor(m.month - 1)
          };
        }),
        A: common_vendor.f(monthlyData.value, (m, k0, i0) => {
          return {
            a: common_vendor.t(m.month),
            b: m.month
          };
        }),
        B: common_vendor.t(peakMonth.value.count > 0 ? peakMonth.value.month + "月是你最勤快的一个月" : "本月还没开始记录，去好好吃饭吧"),
        C: common_vendor.unref(utils_assets.STATIC_BASE_URL) + "/static/guozai/mood_11_proud.png",
        D: common_vendor.t(common_vendor.unref(currentYear)),
        E: common_vendor.t(peakMonth.value.count > 0 ? peakMonth.value.month + "月 · 记录了" + peakMonth.value.count + "天" : "记录中…"),
        F: common_vendor.t((((_c = yearStats.value) == null ? void 0 : _c.longestStreak) || 0) + "天 · 坚持就是胜利"),
        G: common_vendor.t(top3.value.length ? top3.value[0].name + " · " + top3.value[0].count + "次" : "记录中…"),
        H: common_vendor.unref(utils_assets.STATIC_BASE_URL) + "/static/guozai/mood_10_content.png",
        I: common_vendor.unref(utils_assets.STATIC_BASE_URL) + "/static/guozai/action_06_glasses.png",
        J: common_vendor.f(messageLines.value, (line, i, i0) => {
          return {
            a: common_vendor.t(line),
            b: i
          };
        }),
        K: common_vendor.unref(utils_assets.STATIC_BASE_URL) + "/static/guozai/action_09_celebrate.png",
        L: common_vendor.t(common_vendor.unref(currentYear)),
        M: common_vendor.t(((_d = yearStats.value) == null ? void 0 : _d.totalDays) || 0),
        N: common_vendor.t(((_e = yearStats.value) == null ? void 0 : _e.totalRecords) || 0),
        O: common_vendor.t(((_f = yearStats.value) == null ? void 0 : _f.totalRecords) || 0),
        P: common_vendor.t(avgFreq.value),
        Q: common_vendor.t(top3.value.length ? top3.value[0].name : "—"),
        R: common_vendor.t(top3.value.length ? top3.value[0].count + "次本命菜" : "记录中…"),
        S: common_vendor.unref(utils_assets.STATIC_BASE_URL) + "/static/guozai/action_09_celebrate.png",
        T: common_vendor.t(common_vendor.unref(currentYear)),
        U: common_vendor.o(share),
        V: common_vendor.t(common_vendor.unref(currentYear)),
        W: currentPage.value,
        X: common_vendor.o(onSwiperChange)
      }), {
        g: error.value,
        Y: !loading.value && !error.value && currentPage.value < totalPages - 1
      }, !loading.value && !error.value && currentPage.value < totalPages - 1 ? {} : {}, {
        Z: !loading.value && !error.value
      }, !loading.value && !error.value ? {
        aa: common_vendor.f(totalPages, (i, k0, i0) => {
          return {
            a: i,
            b: currentPage.value === i - 1 ? 1 : ""
          };
        })
      } : {});
    };
  }
});
const MiniProgramPage = /* @__PURE__ */ common_vendor._export_sfc(_sfc_main, [["__scopeId", "data-v-804f1809"]]);
exports.MiniProgramPage = MiniProgramPage;
