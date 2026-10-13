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
const _sfc_main = /* @__PURE__ */ common_vendor.defineComponent({
  __name: "index",
  setup(__props) {
    const router = common_vendor.useRouter();
    const { isFav, toggle, syncFromServer } = composables_useWishlist.useWishlist();
    function keyOf(r) {
      return String(r.id ?? r.name);
    }
    const loading = common_vendor.ref(true);
    const recipes = common_vendor.ref([]);
    const keyword = common_vendor.ref("");
    const sourceFilter = common_vendor.ref("ALL");
    const timeFilter = common_vendor.ref("ALL");
    const moodFilter = common_vendor.ref("ALL");
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
    const moodTags = common_vendor.computed(() => {
      const set = /* @__PURE__ */ new Set();
      for (const r of recipes.value) {
        for (const t of (r.moodTags || "").split(",")) {
          const s = t.trim();
          if (s)
            set.add(s);
        }
      }
      return [...set].slice(0, 12);
    });
    const filtered = common_vendor.computed(() => {
      const kw = keyword.value.trim().toLowerCase();
      return recipes.value.filter((r) => {
        if (kw && !`${r.name} ${r.description} ${r.ingredients}`.toLowerCase().includes(kw))
          return false;
        if (sourceFilter.value !== "ALL" && r.source !== sourceFilter.value)
          return false;
        if (timeFilter.value === "FAST" && (r.cookingTime || 0) > 30)
          return false;
        if (timeFilter.value === "SLOW" && (r.cookingTime || 0) <= 30)
          return false;
        if (moodFilter.value !== "ALL" && !(r.moodTags || "").split(",").map((s) => s.trim()).includes(moodFilter.value))
          return false;
        return true;
      });
    });
    common_vendor.onShow(async () => {
      syncFromServer();
      if (recipes.value.length)
        return;
      loading.value = true;
      try {
        const all = await api_recipes.fetchAllRecipes();
        const seen = /* @__PURE__ */ new Set();
        recipes.value = all.filter((r) => {
          if (seen.has(r.name))
            return false;
          seen.add(r.name);
          return true;
        });
      } catch (e) {
        utils_toast.toastError(e, "菜谱库加载失败");
      } finally {
        loading.value = false;
      }
    });
    function resetFilters() {
      keyword.value = "";
      sourceFilter.value = "ALL";
      timeFilter.value = "ALL";
      moodFilter.value = "ALL";
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
        d: keyword.value,
        e: common_vendor.o(($event) => keyword.value = $event.detail.value),
        f: keyword.value
      }, keyword.value ? {
        g: common_vendor.o(($event) => keyword.value = "")
      } : {}, {
        h: common_vendor.f(SOURCE_OPTIONS, (opt, k0, i0) => {
          return {
            a: common_vendor.t(opt.label),
            b: opt.value,
            c: sourceFilter.value === opt.value ? 1 : "",
            d: common_vendor.o(($event) => sourceFilter.value = opt.value, opt.value)
          };
        }),
        i: common_vendor.f(TIME_OPTIONS, (opt, k0, i0) => {
          return {
            a: common_vendor.t(opt.label),
            b: opt.value,
            c: timeFilter.value === opt.value ? 1 : "",
            d: common_vendor.o(($event) => timeFilter.value = opt.value, opt.value)
          };
        }),
        j: moodTags.value.length
      }, moodTags.value.length ? {
        k: moodFilter.value === "ALL" ? 1 : "",
        l: common_vendor.o(($event) => moodFilter.value = "ALL"),
        m: common_vendor.f(moodTags.value, (m, k0, i0) => {
          return {
            a: common_vendor.t(m),
            b: m,
            c: moodFilter.value === m ? 1 : "",
            d: common_vendor.o(($event) => moodFilter.value = m, m)
          };
        })
      } : {}, {
        n: common_vendor.t(filtered.value.length),
        o: keyword.value || sourceFilter.value !== "ALL" || timeFilter.value !== "ALL" || moodFilter.value !== "ALL"
      }, keyword.value || sourceFilter.value !== "ALL" || timeFilter.value !== "ALL" || moodFilter.value !== "ALL" ? {
        p: common_vendor.o(resetFilters)
      } : {}, {
        q: loading.value
      }, loading.value ? {} : !filtered.value.length ? {
        s: `${common_vendor.unref(utils_assets.STATIC_BASE_URL)}/static/guozai/action_08_peek.png`
      } : {
        t: common_vendor.f(filtered.value, (r, k0, i0) => {
          return common_vendor.e({
            a: r.image || `${common_vendor.unref(utils_assets.STATIC_BASE_URL)}/static/guozai/action_01_bowl.png`,
            b: common_vendor.t(r.name),
            c: common_vendor.t(r.description),
            d: r.cookingTime
          }, r.cookingTime ? {
            e: common_vendor.t(r.cookingTime)
          } : {}, {
            f: r.difficulty
          }, r.difficulty ? {
            g: common_vendor.t(r.difficulty)
          } : {}, {
            h: common_vendor.t(r.source === "AI" ? "AI" : "本地"),
            i: common_vendor.unref(isFav)(keyOf(r)) ? 1 : "",
            j: common_vendor.unref(isFav)(keyOf(r)) ? "取消收藏" : "加入想吃清单",
            k: common_vendor.o(($event) => common_vendor.unref(toggle)(keyOf(r)), r.id || r.name),
            l: r.id || r.name,
            m: `查看${r.name}做法`,
            n: common_vendor.o(($event) => openCook(r), r.id || r.name)
          });
        })
      }, {
        r: !filtered.value.length
      });
    };
  }
});
const MiniProgramPage = /* @__PURE__ */ common_vendor._export_sfc(_sfc_main, [["__scopeId", "data-v-c4caa62b"]]);
exports.MiniProgramPage = MiniProgramPage;
