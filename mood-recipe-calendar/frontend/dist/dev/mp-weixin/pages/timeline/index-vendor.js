"use strict";
const common_vendor = require("../../common/vendor.js");
const composables_useNavBar = require("../../composables/useNavBar.js");
const utils_assets = require("../../utils/assets.js");
const utils_login = require("../../utils/login.js");
const utils_toast = require("../../utils/toast.js");
const api_records = require("../../api/records.js");
const api_recipes = require("../../api/recipes.js");
const utils_cookingDraft = require("../../utils/cookingDraft.js");
const __unplugin_components_0 = () => "../../node-modules/@wot-ui/ui/components/wd-navbar/wd-navbar.js";
if (!Array) {
  const _component_wd_navbar = __unplugin_components_0;
  const _component_layout_default_uni = common_vendor.resolveComponent("layout-default-uni");
  (_component_wd_navbar + _component_layout_default_uni)();
}
if (!Math) {
  (LoadingState + EmptyState)();
}
const LoadingState = () => "../../components/guozai/LoadingState.js";
const EmptyState = () => "../../components/guozai/EmptyState.js";
const _sfc_main = /* @__PURE__ */ common_vendor.defineComponent({
  __name: "index",
  setup(__props) {
    const router = common_vendor.useRouter();
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
    async function load() {
      loading.value = true;
      try {
        records.value = await api_records.fetchRecords(await utils_login.ensureLogin());
        const targetId = Number(common_vendor.index.getStorageSync("mrc_timeline_record_id"));
        if (targetId) {
          selected.value = records.value.find((item) => item.id === targetId) || null;
          common_vendor.index.removeStorageSync("mrc_timeline_record_id");
        }
      } catch (e) {
        utils_toast.toastError(e, "时光机加载失败，请重试");
      } finally {
        loading.value = false;
      }
    }
    async function openLinkedRecipe(restart) {
      var _a, _b, _c;
      const id = Number((_a = selected.value) == null ? void 0 : _a.recipeId);
      if (!id || openingRecipe.value)
        return;
      if (!restart) {
        const recordMood = ((_b = selected.value) == null ? void 0 : _b.moodTag) || "平静";
        selected.value = null;
        router.push({ name: "recipe", query: { recipeId: String(id), mood: recordMood } });
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
      await api_records.deleteRecord(selected.value.id, await utils_login.ensureLogin());
      records.value = records.value.filter((item) => {
        var _a;
        return item.id !== ((_a = selected.value) == null ? void 0 : _a.id);
      });
      selected.value = null;
      utils_toast.toastSuccess("记录已删除");
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
              return common_vendor.e({
                a: common_vendor.t(dayNumber(item.recordDate)),
                b: common_vendor.t(weekday(item.recordDate)),
                c: item.imageUrl,
                d: item.dishName,
                e: common_vendor.t(item.moodTag),
                f: common_vendor.t(item.dishName),
                g: common_vendor.t(item.cookingTime || 30),
                h: guozaiSticker(itemIndex),
                i: item.note
              }, item.note ? {
                j: common_vendor.t(item.note)
              } : {}, {
                k: itemIndex % 2 ? 1 : "",
                l: `查看 ${item.recordDate} 的${item.dishName}记录`,
                m: common_vendor.o(($event) => selected.value = item, item.id),
                n: item.id
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
        q: selected.value.dishName,
        r: common_vendor.t(dayNumber(selected.value.recordDate)),
        s: common_vendor.t(weekday(selected.value.recordDate)),
        t: common_vendor.t(selected.value.dishName),
        v: common_vendor.t(selected.value.recordDate),
        w: common_vendor.t(selected.value.moodTag),
        x: common_vendor.t(selected.value.cookingTime || 30),
        y: selected.value.note
      }, selected.value.note ? {
        z: common_vendor.t(selected.value.note)
      } : {}, {
        A: Number(selected.value.recipeId)
      }, Number(selected.value.recipeId) ? {
        B: common_vendor.o(($event) => openLinkedRecipe(false)),
        C: common_vendor.t(openingRecipe.value ? "正在打开…" : "再做一次"),
        D: common_vendor.o(($event) => openLinkedRecipe(true))
      } : {}, {
        E: common_vendor.o(removeSelected),
        F: common_vendor.o(($event) => selected.value = null),
        G: common_vendor.o(($event) => selected.value = null)
      }) : {});
    };
  }
});
const MiniProgramPage = /* @__PURE__ */ common_vendor._export_sfc(_sfc_main, [["__scopeId", "data-v-76e33a35"]]);
exports.MiniProgramPage = MiniProgramPage;
