"use strict";
const common_vendor = require("../../common/vendor.js");
const utils_assets = require("../../utils/assets.js");
const api_recipes = require("../../api/recipes.js");
const utils_cookingDraft = require("../../utils/cookingDraft.js");
const composables_useNavBar = require("../../composables/useNavBar.js");
const composables_useWishlist = require("../../composables/useWishlist.js");
const utils_toast = require("../../utils/toast.js");
const __unplugin_components_1 = () => "../../node-modules/@wot-ui/ui/components/wd-icon/wd-icon.js";
const __unplugin_components_0 = () => "../../node-modules/@wot-ui/ui/components/wd-navbar/wd-navbar.js";
if (!Array) {
  const _component_wd_navbar = __unplugin_components_0;
  const _component_wd_icon = __unplugin_components_1;
  const _component_layout_default_uni = common_vendor.resolveComponent("layout-default-uni");
  (_component_wd_navbar + _component_wd_icon + _component_layout_default_uni)();
}
const size = 20;
const _sfc_main = /* @__PURE__ */ common_vendor.defineComponent({
  __name: "index",
  setup(__props) {
    const router = common_vendor.useRouter();
    const { isFav, toggle, syncFromServer } = composables_useWishlist.useWishlist();
    function keyOf(r) {
      return String(r.id ?? r.name);
    }
    const loading = common_vendor.ref(true);
    const loadingMore = common_vendor.ref(false);
    const finished = common_vendor.ref(false);
    const recipes = common_vendor.ref([]);
    const total = common_vendor.ref(0);
    const page = common_vendor.ref(1);
    const keyword = common_vendor.ref("");
    const sourceFilter = common_vendor.ref("ALL");
    const timeFilter = common_vendor.ref("ALL");
    const moodFilter = common_vendor.ref("ALL");
    const moodTags = common_vendor.ref([]);
    const SOURCE_OPTIONS = [
      { value: "ALL", label: "全部" },
      { value: "AI", label: "AI 菜" },
      { value: "LOCAL", label: "本地菜" }
    ];
    const TIME_OPTIONS = [
      { value: "ALL", label: "不限时长" },
      { value: "FAST", label: "快手 ≤30分" },
      { value: "SLOW", label: "慢工 >30分" }
    ];
    const hasFilters = common_vendor.computed(
      () => !!keyword.value.trim() || sourceFilter.value !== "ALL" || timeFilter.value !== "ALL" || moodFilter.value !== "ALL"
    );
    const IMG_HEIGHTS = [300, 360, 420];
    function imageHeightOf(r) {
      const seed = (r.id ?? 0) + r.name.length;
      return `${IMG_HEIGHTS[seed % IMG_HEIGHTS.length]}rpx`;
    }
    function estimateHeight(r) {
      const seed = (r.id ?? 0) + r.name.length;
      const img = IMG_HEIGHTS[seed % IMG_HEIGHTS.length];
      const desc = r.description && r.description.length > 22 ? 68 : 44;
      return img + desc + 160;
    }
    const columns = common_vendor.computed(() => {
      const left = [];
      const right = [];
      const heights = [0, 0];
      for (const r of recipes.value) {
        const idx = heights[0] <= heights[1] ? 0 : 1;
        if (idx === 0)
          left.push(r);
        else
          right.push(r);
        heights[idx] += estimateHeight(r);
      }
      return [left, right];
    });
    function buildParams(resetPage) {
      return {
        page: resetPage - 1,
        size,
        keyword: keyword.value.trim() || void 0,
        source: sourceFilter.value === "ALL" ? void 0 : sourceFilter.value,
        time: timeFilter.value === "ALL" ? void 0 : timeFilter.value,
        mood: moodFilter.value === "ALL" ? void 0 : moodFilter.value
      };
    }
    let reqSeq = 0;
    async function loadPage(reset) {
      if (reset) {
        page.value = 1;
        finished.value = false;
        loading.value = true;
      } else {
        if (loadingMore.value || finished.value || loading.value)
          return;
        loadingMore.value = true;
        page.value += 1;
      }
      const seq = ++reqSeq;
      const targetPage = page.value;
      try {
        const res = await api_recipes.fetchRecipeLibrary(buildParams(targetPage));
        if (seq !== reqSeq)
          return;
        if (reset) {
          recipes.value = res.list;
        } else {
          recipes.value = [...recipes.value, ...res.list];
        }
        total.value = res.total;
        if (!res.list.length || res.list.length < size)
          finished.value = true;
      } catch (e) {
        if (seq !== reqSeq)
          return;
        if (reset)
          utils_toast.toastError(e, "菜谱库加载失败");
        else
          page.value = Math.max(1, page.value - 1);
      } finally {
        if (seq === reqSeq) {
          loading.value = false;
          loadingMore.value = false;
        }
      }
    }
    async function loadMoodTags() {
      try {
        moodTags.value = await api_recipes.fetchMoodTags();
      } catch {
      }
    }
    function onFilterChange() {
      loadPage(true);
    }
    function onSearchConfirm() {
      if (keywordTimer) {
        clearTimeout(keywordTimer);
        keywordTimer = null;
      }
      loadPage(true);
    }
    let keywordTimer = null;
    let skipKeywordWatch = false;
    common_vendor.watch(keyword, () => {
      if (skipKeywordWatch) {
        skipKeywordWatch = false;
        return;
      }
      if (keywordTimer)
        clearTimeout(keywordTimer);
      keywordTimer = setTimeout(() => {
        keywordTimer = null;
        loadPage(true);
      }, 400);
    });
    common_vendor.onUnmounted(() => {
      if (keywordTimer)
        clearTimeout(keywordTimer);
    });
    common_vendor.onReachBottom(() => {
      if (!loading.value && !finished.value)
        loadPage(false);
    });
    common_vendor.onShow(async () => {
      syncFromServer();
      loadMoodTags();
      if (recipes.value.length || loading.value)
        return;
      loadPage(true);
    });
    function resetFilters() {
      if (keywordTimer) {
        clearTimeout(keywordTimer);
        keywordTimer = null;
      }
      skipKeywordWatch = keyword.value !== "";
      keyword.value = "";
      sourceFilter.value = "ALL";
      timeFilter.value = "ALL";
      moodFilter.value = "ALL";
      loadPage(true);
    }
    function openCook(r) {
      utils_cookingDraft.saveCookingDraft(r, "满足");
      router.push({ name: "cooking" });
    }
    return (_ctx, _cache) => {
      return common_vendor.e({
        a: common_vendor.o(common_vendor.unref(composables_useNavBar.navBack)),
        b: common_vendor.p({
          title: "菜谱库",
          ["left-arrow"]: true,
          ["safe-area-inset-top"]: true,
          ["custom-style"]: "background-color: transparent !important;"
        }),
        c: common_vendor.p({
          name: "search",
          size: "32rpx",
          color: "#A1826A"
        }),
        d: common_vendor.o(onSearchConfirm),
        e: keyword.value,
        f: common_vendor.o(($event) => keyword.value = $event.detail.value),
        g: keyword.value
      }, keyword.value ? {
        h: common_vendor.o(($event) => keyword.value = "")
      } : {}, {
        i: common_vendor.f(SOURCE_OPTIONS, (opt, k0, i0) => {
          return {
            a: common_vendor.t(opt.label),
            b: opt.value,
            c: sourceFilter.value === opt.value ? 1 : "",
            d: common_vendor.o(($event) => {
              sourceFilter.value = opt.value;
              onFilterChange();
            }, opt.value)
          };
        }),
        j: common_vendor.f(TIME_OPTIONS, (opt, k0, i0) => {
          return {
            a: common_vendor.t(opt.label),
            b: opt.value,
            c: timeFilter.value === opt.value ? 1 : "",
            d: common_vendor.o(($event) => {
              timeFilter.value = opt.value;
              onFilterChange();
            }, opt.value)
          };
        }),
        k: moodTags.value.length
      }, moodTags.value.length ? {
        l: moodFilter.value === "ALL" ? 1 : "",
        m: common_vendor.o(($event) => {
          moodFilter.value = "ALL";
          onFilterChange();
        }),
        n: common_vendor.f(moodTags.value, (m, k0, i0) => {
          return {
            a: common_vendor.t(m),
            b: m,
            c: moodFilter.value === m ? 1 : "",
            d: common_vendor.o(($event) => {
              moodFilter.value = m;
              onFilterChange();
            }, m)
          };
        })
      } : {}, {
        o: common_vendor.t(total.value),
        p: hasFilters.value
      }, hasFilters.value ? {
        q: common_vendor.o(resetFilters)
      } : {}, {
        r: loading.value
      }, loading.value ? {} : !recipes.value.length ? {
        t: `${common_vendor.unref(utils_assets.STATIC_BASE_URL)}/static/guozai/action_08_peek.png`
      } : {
        v: common_vendor.f(columns.value, (col, ci, i0) => {
          return {
            a: common_vendor.f(col, (r, k1, i1) => {
              return common_vendor.e({
                a: imageHeightOf(r),
                b: r.image || `${common_vendor.unref(utils_assets.STATIC_BASE_URL)}/static/guozai/action_01_bowl.png`,
                c: common_vendor.unref(isFav)(keyOf(r)) ? 1 : "",
                d: common_vendor.unref(isFav)(keyOf(r)) ? "取消收藏" : "加入想吃清单",
                e: common_vendor.o(($event) => common_vendor.unref(toggle)(keyOf(r)), r.id || r.name),
                f: common_vendor.t(r.name),
                g: r.description
              }, r.description ? {
                h: common_vendor.t(r.description)
              } : {}, {
                i: r.cookingTime
              }, r.cookingTime ? {
                j: common_vendor.t(r.cookingTime)
              } : {}, {
                k: r.difficulty
              }, r.difficulty ? {
                l: common_vendor.t(r.difficulty)
              } : {}, {
                m: common_vendor.t(r.source === "AI" ? "AI" : "本地"),
                n: r.id || r.name,
                o: `查看${r.name}做法`,
                p: common_vendor.o(($event) => openCook(r), r.id || r.name)
              });
            }),
            b: ci
          };
        })
      }, {
        s: !recipes.value.length,
        w: loadingMore.value
      }, loadingMore.value ? {} : finished.value ? {
        y: common_vendor.t(total.value)
      } : {}, {
        x: finished.value
      });
    };
  }
});
const MiniProgramPage = /* @__PURE__ */ common_vendor._export_sfc(_sfc_main, [["__scopeId", "data-v-c4caa62b"]]);
exports.MiniProgramPage = MiniProgramPage;
