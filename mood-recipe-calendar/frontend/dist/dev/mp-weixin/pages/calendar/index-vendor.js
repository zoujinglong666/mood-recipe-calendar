"use strict";
const common_vendor = require("../../common/vendor.js");
const composables_useNavBar = require("../../composables/useNavBar.js");
const utils_assets = require("../../utils/assets.js");
const api_request = require("../../api/request.js");
const api_records = require("../../api/records.js");
const utils_login = require("../../utils/login.js");
const utils_bus = require("../../utils/bus.js");
const __unplugin_components_1 = () => "../../node-modules/@wot-ui/ui/components/wd-image-preview/wd-image-preview.js";
const __unplugin_components_0 = () => "../../node-modules/@wot-ui/ui/components/wd-navbar/wd-navbar.js";
if (!Array) {
  const _component_wd_navbar = __unplugin_components_0;
  const _component_wd_image_preview = __unplugin_components_1;
  const _component_layout_default_uni = common_vendor.resolveComponent("layout-default-uni");
  (_component_wd_navbar + _component_wd_image_preview + _component_layout_default_uni)();
}
if (!Math) {
  (LoadingState + ErrorState + GuozaiImage)();
}
const ErrorState = () => "../../components/guozai/ErrorState.js";
const LoadingState = () => "../../components/guozai/LoadingState.js";
const GuozaiImage = () => "../../components/guozai/GuozaiImage.js";
const RECORD_DATE_KEY = "mrc_record_date";
const _sfc_defineComponent = common_vendor.defineComponent({
  __name: "index",
  setup(__props) {
    common_vendor.onShareAppMessage(() => {
      var _a;
      const pages = getCurrentPages();
      const route2 = ((_a = pages[pages.length - 1]) == null ? void 0 : _a.route) || "";
      return { title: "锅仔 · 按心情帮你决定今天吃什么", path: `/${route2}` };
    });
    common_vendor.onShareTimeline(() => ({ title: "锅仔 · 按心情帮你决定今天吃什么" }));
    const router = common_vendor.useRouter();
    const route = common_vendor.useRoute();
    const { previewImage } = common_vendor.useImagePreview();
    const weekCN = ["日", "一", "二", "三", "四", "五", "六"];
    const now = /* @__PURE__ */ new Date();
    const year = common_vendor.ref(now.getFullYear());
    const month = common_vendor.ref(now.getMonth() + 1);
    const today = now.getDate();
    const monthStr = common_vendor.computed(() => `${year.value}-${String(month.value).padStart(2, "0")}`);
    const monthNumber = common_vendor.computed(() => String(month.value).padStart(2, "0"));
    const loading = common_vendor.ref(true);
    const error = common_vendor.ref("");
    const records = common_vendor.ref([]);
    const stats = common_vendor.ref({ totalDays: 0, currentStreak: 0 });
    const statsError = common_vendor.ref(false);
    const recordMap = common_vendor.computed(() => {
      const map = /* @__PURE__ */ new Map();
      records.value.forEach((r) => {
        var _a;
        const day = Number.parseInt(((_a = r.recordDate) == null ? void 0 : _a.split("-")[2]) || "0", 10);
        if (day > 0) {
          const list = map.get(day);
          if (list)
            list.push(r);
          else
            map.set(day, [r]);
        }
      });
      return map;
    });
    const firstDay = common_vendor.computed(() => new Date(year.value, month.value - 1, 1).getDay());
    const daysInMonth = common_vendor.computed(() => new Date(year.value, month.value, 0).getDate());
    const calCells = common_vendor.computed(() => {
      var _a, _b;
      const cells = [];
      for (let i = 0; i < firstDay.value; i++)
        cells.push(null);
      for (let d = 1; d <= daysInMonth.value; d++) {
        const recs = recordMap.value.get(d);
        cells.push({
          day: d,
          hasRecord: !!(recs == null ? void 0 : recs.length),
          isToday: d === today && month.value === now.getMonth() + 1 && year.value === now.getFullYear(),
          dishImg: ((_a = recs == null ? void 0 : recs[0]) == null ? void 0 : _a.imageUrl) ? api_request.resolveAssetUrl(recs[0].imageUrl) : "",
          mood: ((_b = recs == null ? void 0 : recs[0]) == null ? void 0 : _b.moodTag) || "",
          count: (recs == null ? void 0 : recs.length) || 0
        });
      }
      while (cells.length % 7 !== 0)
        cells.push(null);
      return cells;
    });
    const showEmpty = common_vendor.ref(false);
    const emptyDay = common_vendor.ref(0);
    const showDetail = common_vendor.ref(false);
    const detailRecords = common_vendor.ref([]);
    const detailIndex = common_vendor.ref(0);
    const detailRecord = common_vendor.computed(() => detailRecords.value[detailIndex.value] ?? null);
    let initialDayHandled = false;
    async function loadData() {
      loading.value = true;
      error.value = "";
      try {
        await utils_login.ensureLogin();
        records.value = await api_records.fetchRecordsByMonth(monthStr.value);
        api_records.fetchStats().then((s) => {
          stats.value = { totalDays: s.totalDays, currentStreak: s.currentStreak };
          statsError.value = false;
        }).catch(() => {
          statsError.value = true;
        });
        if (!initialDayHandled) {
          initialDayHandled = true;
          const selectedDay = Number(route.query.day);
          const selRecs = recordMap.value.get(selectedDay);
          if (selRecs == null ? void 0 : selRecs.length) {
            detailRecords.value = selRecs;
            detailIndex.value = 0;
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
    const canGoNext = common_vendor.computed(() => {
      const max = /* @__PURE__ */ new Date();
      return !(year.value > max.getFullYear() || year.value === max.getFullYear() && month.value >= max.getMonth() + 1);
    });
    function reloadMonth() {
      loadData();
    }
    function prevMonth() {
      if (month.value === 1) {
        month.value = 12;
        year.value--;
      } else {
        month.value--;
      }
      reloadMonth();
    }
    function nextMonth() {
      if (!canGoNext.value)
        return;
      if (month.value === 12) {
        month.value = 1;
        year.value++;
      } else {
        month.value++;
      }
      reloadMonth();
    }
    function retryStats() {
      api_records.fetchStats().then((s) => {
        stats.value = { totalDays: s.totalDays, currentStreak: s.currentStreak };
        statsError.value = false;
      }).catch(() => {
        statsError.value = true;
      });
    }
    utils_bus.bus.on(utils_bus.MRC_EVENTS.RECORDS_CHANGED, reloadMonth);
    common_vendor.onUnmounted(() => utils_bus.bus.off(utils_bus.MRC_EVENTS.RECORDS_CHANGED, reloadMonth));
    function goAlbum() {
      router.push({ name: "album" });
    }
    function handleCellClick(c) {
      if (c.hasRecord) {
        const recs = recordMap.value.get(c.day);
        if (recs == null ? void 0 : recs.length) {
          detailRecords.value = recs;
          detailIndex.value = 0;
          showDetail.value = true;
        }
      } else {
        emptyDay.value = c.day;
        showEmpty.value = true;
      }
    }
    function prevDetail() {
      if (detailRecords.value.length > 1)
        detailIndex.value = (detailIndex.value - 1 + detailRecords.value.length) % detailRecords.value.length;
    }
    function nextDetail() {
      if (detailRecords.value.length > 1)
        detailIndex.value = (detailIndex.value + 1) % detailRecords.value.length;
    }
    function onDetailSwipe(e) {
      var _a;
      if (typeof ((_a = e == null ? void 0 : e.detail) == null ? void 0 : _a.current) === "number")
        detailIndex.value = e.detail.current;
    }
    function goRecordFromEmpty() {
      showEmpty.value = false;
      common_vendor.index.setStorageSync(RECORD_DATE_KEY, `${year.value}-${String(month.value).padStart(2, "0")}-${String(emptyDay.value).padStart(2, "0")}`);
      router.pushTab({ name: "record" });
    }
    const photoRecords = common_vendor.computed(() => records.value.filter((item) => Boolean(item.imageUrl)));
    function previewRecordPhoto(record) {
      if (!record)
        return;
      const photos = photoRecords.value;
      if (!photos.length)
        return;
      previewImage({
        images: photos.map((item) => api_request.resolveAssetUrl(item.imageUrl)),
        startPosition: Math.max(0, photos.findIndex((item) => item.id === record.id)),
        closeOnClick: true,
        loop: photos.length > 1
      });
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
        g: common_vendor.t(year.value),
        h: common_vendor.t(monthNumber.value),
        i: common_vendor.t(recordMap.value.size),
        j: common_vendor.t(statsError.value ? "—" : stats.value.currentStreak),
        k: common_vendor.o(retryStats),
        l: `${common_vendor.unref(utils_assets.STATIC_BASE_URL)}/static/guozai/action_04_calendar.png`,
        m: common_vendor.o(prevMonth),
        n: common_vendor.t(month.value),
        o: !canGoNext.value ? 1 : "",
        p: common_vendor.o(nextMonth),
        q: common_vendor.f(weekCN, (w, k0, i0) => {
          return {
            a: common_vendor.t(w),
            b: w
          };
        }),
        r: common_vendor.f(calCells.value, (c, i, i0) => {
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
            f: common_vendor.t(c.day),
            g: c.count > 1
          }, c.count > 1 ? {
            h: common_vendor.t(c.count)
          } : {}) : {
            i: common_vendor.t(c.day)
          }, {
            j: c.isToday
          }, c.isToday ? {} : {}) : {}, {
            k: i,
            l: c && c.isToday ? 1 : "",
            m: c && c.hasRecord ? 1 : "",
            n: c ? "button" : void 0,
            o: c ? `${month.value}月${c.day}日，${c.hasRecord ? "查看饮食记录" : "还没有记录，点击去记录"}` : void 0,
            p: common_vendor.o(($event) => c && handleCellClick(c), i)
          });
        }),
        s: `${common_vendor.unref(utils_assets.STATIC_BASE_URL)}/static/guozai/action_08_peek.png`,
        t: `${common_vendor.unref(utils_assets.STATIC_BASE_URL)}/static/guozai/action_05_album.png`,
        v: common_vendor.t(month.value),
        w: common_vendor.o(goAlbum)
      }, {
        e: error.value,
        x: showEmpty.value
      }, showEmpty.value ? {
        y: `${common_vendor.unref(utils_assets.STATIC_BASE_URL)}/static/guozai/state_01_empty.png`,
        z: common_vendor.t(month.value),
        A: common_vendor.t(emptyDay.value),
        B: common_vendor.o(goRecordFromEmpty),
        C: common_vendor.o(() => {
        }),
        D: common_vendor.o(($event) => showEmpty.value = false)
      } : {}, {
        E: showDetail.value && detailRecord.value
      }, showDetail.value && detailRecord.value ? common_vendor.e({
        F: detailRecords.value.length
      }, detailRecords.value.length ? {
        G: common_vendor.f(detailRecords.value, (rec, idx, i0) => {
          return {
            a: common_vendor.o(($event) => previewRecordPhoto(rec), rec.id ?? idx),
            b: "03f4b073-4-" + i0 + ",03f4b073-0",
            c: common_vendor.p({
              src: common_vendor.unref(api_request.resolveAssetUrl)(rec.imageUrl),
              ["placeholder-text"]: "📷",
              ["aria-label"]: "查看这张照片的大图"
            }),
            d: rec.id ?? idx
          };
        }),
        H: detailIndex.value,
        I: common_vendor.o(onDetailSwipe)
      } : {}, {
        J: common_vendor.t(detailRecord.value.dishName),
        K: detailRecords.value.length > 1
      }, detailRecords.value.length > 1 ? {
        L: common_vendor.o(prevDetail),
        M: common_vendor.t(detailIndex.value + 1),
        N: common_vendor.t(detailRecords.value.length),
        O: common_vendor.o(nextDetail)
      } : {}, {
        P: common_vendor.t(detailRecord.value.moodTag),
        Q: detailRecord.value.note
      }, detailRecord.value.note ? {
        R: common_vendor.t(detailRecord.value.note)
      } : {}, {
        S: common_vendor.o(($event) => showDetail.value = false),
        T: common_vendor.o(() => {
        }),
        U: common_vendor.o(($event) => showDetail.value = false)
      }) : {});
    };
  }
});
_sfc_defineComponent.__runtimeHooks = 6;
const MiniProgramPage = /* @__PURE__ */ common_vendor._export_sfc(_sfc_defineComponent, [["__scopeId", "data-v-03f4b073"]]);
exports.MiniProgramPage = MiniProgramPage;
