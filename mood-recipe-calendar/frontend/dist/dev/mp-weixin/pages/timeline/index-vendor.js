"use strict";
const common_vendor = require("../../common/vendor.js");
const composables_useNavBar = require("../../composables/useNavBar.js");
const utils_assets = require("../../utils/assets.js");
const utils_login = require("../../utils/login.js");
const utils_toast = require("../../utils/toast.js");
const api_records = require("../../api/records.js");
const api_recipes = require("../../api/recipes.js");
const utils_cookingDraft = require("../../utils/cookingDraft.js");
const __unplugin_components_2 = () => "../../node-modules/@wot-ui/ui/components/wd-tour/wd-tour.js";
const __unplugin_components_1 = () => "../../node-modules/@wot-ui/ui/components/wd-image-preview/wd-image-preview.js";
const __unplugin_components_0 = () => "../../node-modules/@wot-ui/ui/components/wd-navbar/wd-navbar.js";
if (!Array) {
  const _component_wd_navbar = __unplugin_components_0;
  const _component_wd_image_preview = __unplugin_components_1;
  const _component_wd_tour = __unplugin_components_2;
  const _component_layout_default_uni = common_vendor.resolveComponent("layout-default-uni");
  (_component_wd_navbar + _component_wd_image_preview + _component_wd_tour + _component_layout_default_uni)();
}
if (!Math) {
  (LoadingState + EmptyState)();
}
const LoadingState = () => "../../components/guozai/LoadingState.js";
const EmptyState = () => "../../components/guozai/EmptyState.js";
const TIMELINE_TOUR_KEY = "mrc-tour-timeline-version";
const TIMELINE_TOUR_VERSION = 1;
const _sfc_defineComponent = common_vendor.defineComponent({
  __name: "index",
  setup(__props) {
    common_vendor.onShareAppMessage(() => {
      var _a;
      const pages = getCurrentPages();
      const route = ((_a = pages[pages.length - 1]) == null ? void 0 : _a.route) || "";
      return {
        title: records.value.length ? `我留住了 ${records.value.length} 顿饭的食光` : "锅仔 · 用时光机收好你的餐桌回忆",
        path: `/${route}`
      };
    });
    common_vendor.onShareTimeline(() => ({
      title: records.value.length ? `我留住了 ${records.value.length} 顿饭的食光` : "锅仔 · 用时光机收好你的餐桌回忆"
    }));
    const router = common_vendor.useRouter();
    const { previewImage } = common_vendor.useImagePreview();
    const loading = common_vendor.ref(true);
    const records = common_vendor.ref([]);
    const nearToday = common_vendor.ref(false);
    const selected = common_vendor.ref(null);
    const openingRecipe = common_vendor.ref(false);
    let lastTick = 0;
    const grouped = common_vendor.computed(() => {
      const map = /* @__PURE__ */ new Map();
      records.value.forEach((item) => {
        var _a;
        const key = ((_a = item.recordDate) == null ? void 0 : _a.slice(0, 7)) || "过去的日子";
        map.set(key, [...map.get(key) || [], item]);
      });
      return [...map.entries()].map(([month, items]) => ({ month, items }));
    });
    const memorySummary = common_vendor.computed(() => `留下 ${records.value.length} 顿饭 · 跨过 ${grouped.value.length} 个月`);
    function monthNumber(month) {
      const value = Number(month.slice(5));
      return Number.isFinite(value) ? String(value).padStart(2, "0") : "—";
    }
    function monthYear(month) {
      return /^\d{4}-\d{2}$/.test(month) ? `${month.slice(0, 4)} 年` : month;
    }
    function isDishRecord(item) {
      return Number(item.recipeId) > 0;
    }
    function dayNumber(date) {
      return (date == null ? void 0 : date.slice(8, 10)) || "--";
    }
    function weekday(date) {
      if (!date)
        return "那天";
      const day = (/* @__PURE__ */ new Date(`${date}T12:00:00`)).getDay();
      return `周${"日一二三四五六"[day]}`;
    }
    function guozaiSticker(index) {
      return index % 2 ? utils_assets.STATIC_BASE_URL + "/static/guozai/action_10_thinking.png" : utils_assets.STATIC_BASE_URL + "/static/guozai/action_08_peek.png";
    }
    const showTimelineTour = common_vendor.ref(false);
    const timelineTourCurrent = common_vendor.ref(0);
    const timelineTourSteps = [
      { element: "#tour-timeline-hero", content: "食光正在倒带：越往下滚，看到的是越早以前的日子。", placement: "bottom" },
      { element: "#tour-timeline-chip", content: "点任意一张卡片，就能翻开那一天的完整记录。", placement: "bottom" }
    ];
    function startTimelineTour() {
      if (loading.value || !records.value.length || showTimelineTour.value || Number(common_vendor.index.getStorageSync(TIMELINE_TOUR_KEY)) >= TIMELINE_TOUR_VERSION)
        return;
      timelineTourCurrent.value = 0;
      common_vendor.nextTick$1(() => {
        showTimelineTour.value = true;
      });
    }
    function finishTimelineTour() {
      common_vendor.index.setStorageSync(TIMELINE_TOUR_KEY, TIMELINE_TOUR_VERSION);
      showTimelineTour.value = false;
    }
    async function load() {
      loading.value = true;
      try {
        await utils_login.ensureLogin();
        records.value = await api_records.fetchRecords();
      } catch (e) {
        utils_toast.toastError(e, "时光机加载失败，请重试");
      } finally {
        loading.value = false;
        common_vendor.nextTick$1(startTimelineTour);
      }
    }
    function openDetail(record) {
      selected.value = record;
    }
    async function openLinkedRecipe(restart) {
      var _a, _b, _c;
      const id = Number((_a = selected.value) == null ? void 0 : _a.recipeId);
      if (!id || openingRecipe.value)
        return;
      if (!restart) {
        const recordMood = ((_b = selected.value) == null ? void 0 : _b.moodTag) || "平静";
        selected.value = null;
        common_vendor.index.navigateTo({
          url: `/pages/recipe/index?recipeId=${id}&mood=${encodeURIComponent(recordMood)}`
        });
        return;
      }
      openingRecipe.value = true;
      try {
        await utils_login.ensureLogin();
        const recipe = await api_recipes.fetchRecipeDetail(id);
        utils_cookingDraft.saveCookingDraft(recipe, ((_c = selected.value) == null ? void 0 : _c.moodTag) || "平静");
        if (restart)
          common_vendor.index.removeStorageSync(utils_cookingDraft.COOKING_PROGRESS_KEY);
        selected.value = null;
        router.push({ name: "cooking" });
      } catch (e) {
        utils_toast.toastError(e, "菜谱暂时打不开，请重试");
      } finally {
        openingRecipe.value = false;
      }
    }
    function editSelected() {
      var _a;
      const id = (_a = selected.value) == null ? void 0 : _a.id;
      if (!id)
        return;
      selected.value = null;
      router.push({ name: "record-edit", params: { id: String(id) } });
    }
    function onScroll(e) {
      var _a;
      const top = Number(((_a = e.detail) == null ? void 0 : _a.scrollTop) || 0);
      const now = Date.now();
      const next = top < 40;
      if (next !== nearToday.value && now - lastTick > 500) {
        nearToday.value = next;
        lastTick = now;
        try {
          common_vendor.index.vibrateShort({ type: "light" });
        } catch {
        }
      }
    }
    common_vendor.onShow(load);
    async function removeSelected() {
      if (!selected.value)
        return;
      const choice = await common_vendor.index.showModal({ title: "删除这条记录？", content: "删除后无法恢复。", confirmColor: "#D94A43" });
      if (!choice.confirm)
        return;
      await utils_login.ensureLogin();
      await api_records.deleteRecord(selected.value.id);
      records.value = records.value.filter((item) => {
        var _a;
        return item.id !== ((_a = selected.value) == null ? void 0 : _a.id);
      });
      selected.value = null;
      utils_toast.toastSuccess("记录已删除");
    }
    const photoRecords = common_vendor.computed(() => records.value.filter((item) => Boolean(item.imageUrl)));
    function previewRecordPhoto(record) {
      const photos = photoRecords.value;
      if (!(record == null ? void 0 : record.imageUrl) || !photos.length)
        return;
      previewImage({
        images: photos.map((item) => item.imageUrl),
        startPosition: Math.max(0, photos.findIndex((item) => item.id === record.id)),
        closeOnClick: true,
        loop: photos.length > 1
      });
    }
    function previewRecordImage(record, index) {
      var _a;
      const images = ((_a = record.imageUrls) == null ? void 0 : _a.length) ? record.imageUrls : [record.imageUrl];
      previewImage({ images, startPosition: index, closeOnClick: true, loop: images.length > 1 });
    }
    return (_ctx, _cache) => {
      return common_vendor.e({
        a: common_vendor.o(common_vendor.unref(composables_useNavBar.navBack)),
        b: common_vendor.p({
          title: "菜谱时光机",
          ["left-arrow"]: true,
          ["safe-area-inset-top"]: true,
          ["custom-style"]: "background-color: transparent !important;"
        }),
        c: loading.value
      }, loading.value ? {
        d: common_vendor.p({
          text: "锅仔正在翻找你的餐桌回忆…"
        })
      } : !records.value.length ? {
        f: common_vendor.o(($event) => common_vendor.unref(router).pushTab({
          name: "record"
        })),
        g: common_vendor.p({
          image: `${common_vendor.unref(utils_assets.STATIC_BASE_URL)}/static/guozai/action_07_empty.png`,
          title: "时光机还是空的",
          text: "记录第一餐，让锅仔替你把今天收好。",
          ["action-text"]: "去记录"
        })
      } : {
        h: common_vendor.t(memorySummary.value),
        i: `${common_vendor.unref(utils_assets.STATIC_BASE_URL)}/static/guozai/action_04_calendar.png`,
        j: common_vendor.t(nearToday.value ? "此刻 · 今天" : "时间正在向过去流动"),
        k: nearToday.value ? 1 : "",
        l: common_vendor.f(grouped.value, (group, k0, i0) => {
          return {
            a: common_vendor.t(monthNumber(group.month)),
            b: common_vendor.t(monthYear(group.month)),
            c: common_vendor.t(group.items.length),
            d: common_vendor.f(group.items, (item, itemIndex, i1) => {
              var _a;
              return common_vendor.e({
                a: common_vendor.t(dayNumber(item.recordDate)),
                b: common_vendor.t(weekday(item.recordDate)),
                c: common_vendor.f(item.imageUrls || [item.imageUrl], (image, imageIndex, i2) => {
                  return {
                    a: image,
                    b: image,
                    c: common_vendor.o(($event) => previewRecordImage(item, imageIndex), image)
                  };
                }),
                d: item.dishName,
                e: common_vendor.t(item.moodTag),
                f: common_vendor.t(isDishRecord(item) ? "吃了" : "记下了"),
                g: common_vendor.t(item.dishName),
                h: common_vendor.t(isDishRecord(item) ? `${item.cookingTime || 30} 分钟 · 点击翻开` : "随手一记 · 点击翻开"),
                i: guozaiSticker(itemIndex),
                j: common_vendor.n(`timeline-card__photo--${((_a = item.imageUrls) == null ? void 0 : _a.length) || 1}`),
                k: item.note
              }, item.note ? {
                l: common_vendor.t(item.note)
              } : {}, {
                m: common_vendor.t(isDishRecord(item) ? "这一餐，锅仔替你收好了" : "这一味，锅仔替你收好了"),
                n: itemIndex % 2 ? 1 : "",
                o: `查看 ${item.recordDate} 的${item.dishName}记录`,
                p: common_vendor.o(($event) => openDetail(item), item.id),
                q: item.id
              });
            }),
            e: group.month
          };
        }),
        m: `${common_vendor.unref(utils_assets.STATIC_BASE_URL)}/static/guozai/action_08_peek.png`,
        n: common_vendor.o(onScroll)
      }, {
        e: !records.value.length,
        o: selected.value
      }, selected.value ? common_vendor.e({
        p: selected.value.imageUrl,
        q: `查看${selected.value.dishName}的大图`,
        r: common_vendor.o(($event) => previewRecordPhoto(selected.value)),
        s: common_vendor.t(dayNumber(selected.value.recordDate)),
        t: common_vendor.t(weekday(selected.value.recordDate)),
        v: common_vendor.t(selected.value.dishName),
        w: common_vendor.t(selected.value.recordDate),
        x: common_vendor.t(selected.value.moodTag),
        y: common_vendor.t(isDishRecord(selected.value) ? ` · ${selected.value.cookingTime || 30} 分钟` : " · 随手一记"),
        z: selected.value.note
      }, selected.value.note ? {
        A: common_vendor.t(selected.value.note)
      } : {}, {
        B: Number(selected.value.recipeId)
      }, Number(selected.value.recipeId) ? {
        C: common_vendor.o(($event) => openLinkedRecipe(false)),
        D: common_vendor.t(openingRecipe.value ? "正在打开…" : "再做一次"),
        E: common_vendor.o(($event) => openLinkedRecipe(true))
      } : {}, {
        F: common_vendor.o(editSelected),
        G: common_vendor.o(removeSelected),
        H: common_vendor.o(($event) => selected.value = null),
        I: common_vendor.o(() => {
        }),
        J: common_vendor.o(($event) => selected.value = null)
      }) : {}, {
        K: common_vendor.o(finishTimelineTour),
        L: common_vendor.o(finishTimelineTour),
        M: common_vendor.o(($event) => showTimelineTour.value = $event),
        N: common_vendor.o(($event) => timelineTourCurrent.value = $event),
        O: common_vendor.p({
          steps: timelineTourSteps,
          padding: 10,
          ["border-radius"]: 18,
          ["z-index"]: 1200,
          ["custom-nav"]: true,
          ["missing-strategy"]: "skip",
          ["skip-text"]: "不再提示",
          ["finish-text"]: "知道啦",
          ["custom-style"]: "--wot-tour-popover-bg: var(--mrc-surface); --wot-tour-info-color: var(--mrc-text); --wot-tour-popover-radius: 16px; --wot-tour-popover-padding: 18px;",
          modelValue: showTimelineTour.value,
          current: timelineTourCurrent.value
        })
      });
    };
  }
});
_sfc_defineComponent.__runtimeHooks = 6;
const MiniProgramPage = /* @__PURE__ */ common_vendor._export_sfc(_sfc_defineComponent, [["__scopeId", "data-v-76e33a35"]]);
exports.MiniProgramPage = MiniProgramPage;
