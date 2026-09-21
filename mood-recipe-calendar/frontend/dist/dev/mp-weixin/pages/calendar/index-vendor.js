"use strict";
const common_vendor = require("../../common/vendor.js");
const composables_useNavBar = require("../../composables/useNavBar.js");
const utils_assets = require("../../utils/assets.js");
const api_records = require("../../api/records.js");
const utils_login = require("../../utils/login.js");
const __unplugin_components_0 = () => "../../node-modules/@wot-ui/ui/components/wd-navbar/wd-navbar.js";
if (!Array) {
  const _component_wd_navbar = __unplugin_components_0;
  const _component_layout_default_uni = common_vendor.resolveComponent("layout-default-uni");
  (_component_wd_navbar + _component_layout_default_uni)();
}
if (!Math) {
  (LoadingState + ErrorState)();
}
const ErrorState = () => "../../components/guozai/ErrorState.js";
const LoadingState = () => "../../components/guozai/LoadingState.js";
const _sfc_main = /* @__PURE__ */ common_vendor.defineComponent({
  __name: "index",
  setup(__props) {
    const router = common_vendor.useRouter();
    const route = common_vendor.useRoute();
    const weekCN = ["日", "一", "二", "三", "四", "五", "六"];
    const now = /* @__PURE__ */ new Date();
    const year = now.getFullYear();
    const month = now.getMonth() + 1;
    const today = now.getDate();
    const monthStr = `${year}-${String(month).padStart(2, "0")}`;
    const monthNumber = String(month).padStart(2, "0");
    const loading = common_vendor.ref(true);
    const error = common_vendor.ref("");
    const records = common_vendor.ref([]);
    const stats = common_vendor.ref({ totalDays: 0, currentStreak: 0 });
    const recordMap = common_vendor.computed(() => {
      const map = /* @__PURE__ */ new Map();
      records.value.forEach((r) => {
        var _a;
        const day = Number.parseInt(((_a = r.recordDate) == null ? void 0 : _a.split("-")[2]) || "0", 10);
        if (day > 0)
          map.set(day, r);
      });
      return map;
    });
    const firstDay = new Date(year, month - 1, 1).getDay();
    const daysInMonth = new Date(year, month, 0).getDate();
    const calCells = common_vendor.computed(() => {
      const cells = [];
      for (let i = 0; i < firstDay; i++)
        cells.push(null);
      for (let d = 1; d <= daysInMonth; d++) {
        const rec = recordMap.value.get(d);
        cells.push({
          day: d,
          hasRecord: !!rec,
          isToday: d === today,
          dishImg: (rec == null ? void 0 : rec.imageUrl) || "",
          mood: (rec == null ? void 0 : rec.moodTag) || ""
        });
      }
      while (cells.length % 7 !== 0)
        cells.push(null);
      return cells;
    });
    const showEmpty = common_vendor.ref(false);
    const emptyDay = common_vendor.ref(0);
    const showDetail = common_vendor.ref(false);
    const detailRecord = common_vendor.ref(null);
    let initialDayHandled = false;
    async function loadData() {
      loading.value = true;
      error.value = "";
      try {
        const openid = await utils_login.ensureLogin();
        const [monthRecords, statsData] = await Promise.all([
          api_records.fetchRecordsByMonth(openid, monthStr),
          api_records.fetchStats(openid)
        ]);
        records.value = monthRecords;
        stats.value = { totalDays: statsData.totalDays, currentStreak: statsData.currentStreak };
        if (!initialDayHandled) {
          initialDayHandled = true;
          const selectedDay = Number(route.query.day);
          const selectedRecord = recordMap.value.get(selectedDay);
          if (selectedRecord) {
            detailRecord.value = selectedRecord;
            showDetail.value = true;
          }
        }
      } catch (e) {
        error.value = e.message || "加载失败";
      } finally {
        loading.value = false;
      }
    }
    common_vendor.onShow(() => {
      loadData();
    });
    function goAlbum() {
      router.push({ name: "album" });
    }
    function handleCellClick(c) {
      if (c.hasRecord) {
        const rec = recordMap.value.get(c.day);
        if (rec) {
          detailRecord.value = rec;
          showDetail.value = true;
        }
      } else {
        emptyDay.value = c.day;
        showEmpty.value = true;
      }
    }
    function goRecordFromEmpty() {
      showEmpty.value = false;
      router.pushTab({ name: "record" });
    }
    return (_ctx, _cache) => {
      return common_vendor.e({
        a: common_vendor.o(common_vendor.unref(composables_useNavBar.navBack)),
        b: common_vendor.p({
          title: "食光日历",
          ["left-arrow"]: true,
          ["safe-area-inset-top"]: true,
          ["custom-style"]: "background-color: transparent !important;"
        }),
        c: loading.value
      }, loading.value ? {
        d: common_vendor.p({
          text: "锅仔正在翻日历..."
        })
      } : error.value ? {
        f: common_vendor.o(loadData)
      } : {
        g: common_vendor.t(common_vendor.unref(year)),
        h: common_vendor.t(common_vendor.unref(monthNumber)),
        i: common_vendor.t(recordMap.value.size),
        j: common_vendor.t(stats.value.currentStreak),
        k: `${common_vendor.unref(utils_assets.STATIC_BASE_URL)}/static/guozai/action_04_calendar.png`,
        l: common_vendor.t(month),
        m: common_vendor.f(weekCN, (w, k0, i0) => {
          return {
            a: common_vendor.t(w),
            b: w
          };
        }),
        n: common_vendor.f(calCells.value, (c, i, i0) => {
          var _a;
          return common_vendor.e({
            a: c
          }, c ? common_vendor.e({
            b: c.hasRecord
          }, c.hasRecord ? common_vendor.e({
            c: c.dishImg
          }, c.dishImg ? {
            d: c.dishImg
          } : {
            e: common_vendor.t((_a = c.mood) == null ? void 0 : _a.charAt(0))
          }, {
            f: common_vendor.t(c.day)
          }) : {
            g: common_vendor.t(c.day)
          }, {
            h: c.isToday
          }, c.isToday ? {} : {}) : {}, {
            i,
            j: c && c.isToday ? 1 : "",
            k: c && c.hasRecord ? 1 : "",
            l: c ? "button" : void 0,
            m: c ? `${month}月${c.day}日，${c.hasRecord ? "查看饮食记录" : "还没有记录，点击去记录"}` : void 0,
            n: common_vendor.o(($event) => c && handleCellClick(c), i)
          });
        }),
        o: `${common_vendor.unref(utils_assets.STATIC_BASE_URL)}/static/guozai/action_08_peek.png`,
        p: `${common_vendor.unref(utils_assets.STATIC_BASE_URL)}/static/guozai/action_05_album.png`,
        q: common_vendor.t(month),
        r: common_vendor.o(goAlbum)
      }, {
        e: error.value,
        s: showEmpty.value
      }, showEmpty.value ? {
        t: `${common_vendor.unref(utils_assets.STATIC_BASE_URL)}/static/guozai/state_01_empty.png`,
        v: common_vendor.t(month),
        w: common_vendor.t(emptyDay.value),
        x: common_vendor.o(goRecordFromEmpty),
        y: common_vendor.o(() => {
        }),
        z: common_vendor.o(($event) => showEmpty.value = false)
      } : {}, {
        A: showDetail.value && detailRecord.value
      }, showDetail.value && detailRecord.value ? common_vendor.e({
        B: detailRecord.value.imageUrl
      }, detailRecord.value.imageUrl ? {
        C: detailRecord.value.imageUrl
      } : {}, {
        D: common_vendor.t(detailRecord.value.dishName),
        E: common_vendor.t(detailRecord.value.moodTag),
        F: detailRecord.value.note
      }, detailRecord.value.note ? {
        G: common_vendor.t(detailRecord.value.note)
      } : {}, {
        H: common_vendor.o(($event) => showDetail.value = false),
        I: common_vendor.o(() => {
        }),
        J: common_vendor.o(($event) => showDetail.value = false)
      }) : {});
    };
  }
});
const MiniProgramPage = /* @__PURE__ */ common_vendor._export_sfc(_sfc_main, [["__scopeId", "data-v-03f4b073"]]);
exports.MiniProgramPage = MiniProgramPage;
