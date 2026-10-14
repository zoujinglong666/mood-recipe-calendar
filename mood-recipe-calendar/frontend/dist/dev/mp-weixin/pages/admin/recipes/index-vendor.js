"use strict";
const common_vendor = require("../../../common/vendor.js");
const api_admin = require("../../../api/admin.js");
const api_request = require("../../../api/request.js");
const composables_useNavBar = require("../../../composables/useNavBar.js");
const utils_toast = require("../../../utils/toast.js");
const __unplugin_components_1 = () => "../../../node-modules/@wot-ui/ui/components/wd-pagination/wd-pagination.js";
const __unplugin_components_0 = () => "../../../node-modules/@wot-ui/ui/components/wd-navbar/wd-navbar.js";
if (!Array) {
  const _component_wd_navbar = __unplugin_components_0;
  const _component_wd_pagination = __unplugin_components_1;
  const _component_layout_default_uni = common_vendor.resolveComponent("layout-default-uni");
  (_component_wd_navbar + _component_wd_pagination + _component_layout_default_uni)();
}
const size = 20;
const _sfc_main = /* @__PURE__ */ common_vendor.defineComponent({
  __name: "index",
  setup(__props) {
    const router = common_vendor.useRouter();
    const stats = common_vendor.ref(null);
    const list = common_vendor.ref([]);
    const loading = common_vendor.ref(false);
    const page = common_vendor.ref(1);
    const total = common_vendor.ref(0);
    const keyword = common_vendor.ref("");
    const sourceFilter = common_vendor.ref("ALL");
    const onlyPlaceholder = common_vendor.ref(false);
    const genConfig = common_vendor.ref(null);
    const runningGen = common_vendor.ref(false);
    const generating = common_vendor.ref({});
    const batchCount = common_vendor.ref(10);
    const batchRunning = common_vendor.ref(false);
    const SOURCE_OPTIONS = [
      { value: "ALL", label: "全部来源" },
      { value: "AI", label: "AI 生成" },
      { value: "LOCAL", label: "本地种子" }
    ];
    async function loadStats() {
      try {
        stats.value = await api_admin.adminRecipeStats();
      } catch (e) {
        utils_toast.toastError(e.message || "无权限");
        setTimeout(() => router.back(), 1500);
      }
    }
    async function loadList() {
      if (loading.value)
        return;
      loading.value = true;
      try {
        const res = await api_admin.adminRecipeList({
          page: page.value - 1,
          size,
          keyword: keyword.value.trim() || void 0,
          source: sourceFilter.value === "ALL" ? void 0 : sourceFilter.value,
          placeholderImage: onlyPlaceholder.value || void 0
        });
        list.value = res.list;
        total.value = res.total;
      } catch (e) {
        utils_toast.toastError(e.message || "加载失败");
      } finally {
        loading.value = false;
      }
    }
    function onSearch() {
      page.value = 1;
      loadList();
    }
    function onFilterChange() {
      page.value = 1;
      loadList();
    }
    function goEdit(item) {
      router.push({ name: "admin-recipe-edit", params: { id: String(item.id) }, query: { id: String(item.id) } });
    }
    function needsImage(img) {
      return !img || img.startsWith("/static/") || img.includes("doubaocdn.com");
    }
    const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));
    async function genImage(item) {
      if (generating.value[item.id])
        return;
      generating.value[item.id] = true;
      try {
        const res = await api_admin.adminRecipeGenImage(item.id);
        if (!res.started && res.image) {
          item.image = res.image;
          utils_toast.toastSuccess("该菜谱已有配图");
          return;
        }
        utils_toast.toast("AI 配图生成中，约需 1 分钟");
        for (let i = 0; i < 20; i++) {
          await sleep(5e3);
          const detail = await api_admin.adminRecipeDetail(item.id).catch(() => null);
          if (detail && !needsImage(detail.image)) {
            item.image = detail.image;
            utils_toast.toastSuccess("配图生成完成");
            loadStats();
            return;
          }
        }
        utils_toast.toast("生成耗时较长，稍后翻页或重新搜索即可看到配图");
      } catch (e) {
        utils_toast.toastError(e.message || "配图生成失败");
      } finally {
        generating.value[item.id] = false;
      }
    }
    function askDelete(item) {
      common_vendor.index.showModal({
        title: "删除菜谱",
        content: `确定删除「${item.name}」？删除后不可恢复。`,
        confirmText: "删除",
        confirmColor: "#D94841",
        success: (result) => {
          if (result.confirm)
            doDelete(item);
        }
      });
    }
    async function doDelete(item) {
      try {
        await api_admin.adminRecipeDelete(item.id);
        utils_toast.toastSuccess("已删除");
        list.value = list.value.filter((r) => r.id !== item.id);
        total.value = Math.max(0, total.value - 1);
        loadStats();
        if (!list.value.length && page.value > 1) {
          page.value -= 1;
        }
        loadList();
      } catch (e) {
        utils_toast.toastError(e.message || "删除失败");
      }
    }
    async function loadGenConfig() {
      try {
        genConfig.value = await api_admin.adminGetGenerationConfig();
      } catch (e) {
      }
    }
    async function toggleEnabled() {
      if (!genConfig.value)
        return;
      try {
        const res = await api_admin.adminUpdateGenerationConfig({ enabled: !genConfig.value.enabled });
        genConfig.value = { ...genConfig.value, ...res };
      } catch (e) {
        utils_toast.toastError(e.message || "保存失败");
      }
    }
    async function changeCount(delta) {
      if (!genConfig.value)
        return;
      const next = Math.max(1, Math.min(30, genConfig.value.dailyCount + delta));
      try {
        const res2 = await api_admin.adminUpdateGenerationConfig({ dailyCount: next });
        genConfig.value = { ...genConfig.value, ...res2 };
      } catch (e) {
        utils_toast.toastError(e.message || "保存失败");
      }
    }
    async function runNow() {
      if (runningGen.value)
        return;
      runningGen.value = true;
      try {
        const res = await api_admin.adminRunGenerationNow();
        common_vendor.index.showModal({ title: "已开始", content: res.message, showCancel: false, confirmText: "好的" });
        await loadGenConfig();
        await loadStats();
        await loadList();
      } catch (e) {
        utils_toast.toastError(e.message || "生成失败");
      } finally {
        runningGen.value = false;
      }
    }
    function batchChangeCount(delta) {
      batchCount.value = Math.max(1, Math.min(100, batchCount.value + delta));
    }
    async function batchImages() {
      if (batchRunning.value)
        return;
      batchRunning.value = true;
      try {
        const res = await api_admin.adminRecipeBatchImage(batchCount.value);
        utils_toast.toastSuccess(`已为 ${res.queued} 道缺图菜谱排队生成配图`);
        await loadStats();
        await loadList();
      } catch (e) {
        utils_toast.toastError(e.message || "批量生成失败");
      } finally {
        batchRunning.value = false;
      }
    }
    common_vendor.onShow(() => {
      loadStats();
      loadList();
      loadGenConfig();
    });
    return (_ctx, _cache) => {
      var _a;
      return common_vendor.e({
        a: common_vendor.o(common_vendor.unref(composables_useNavBar.navBack)),
        b: common_vendor.p({
          title: "菜谱管理中心",
          ["left-arrow"]: true,
          ["safe-area-inset-top"]: true,
          ["custom-style"]: "background-color: transparent !important;"
        }),
        c: stats.value
      }, stats.value ? {
        d: common_vendor.t(stats.value.total),
        e: common_vendor.t(stats.value.placeholderImage),
        f: common_vendor.t(stats.value.ai),
        g: common_vendor.t(stats.value.local)
      } : {}, {
        h: genConfig.value
      }, genConfig.value ? common_vendor.e({
        i: genConfig.value.enabled,
        j: common_vendor.o(toggleEnabled),
        k: common_vendor.o(($event) => changeCount(-1)),
        l: common_vendor.t(genConfig.value.dailyCount),
        m: common_vendor.o(($event) => changeCount(1)),
        n: genConfig.value.lastRunAt
      }, genConfig.value.lastRunAt ? {
        o: common_vendor.t((_a = genConfig.value.lastRunAt) == null ? void 0 : _a.substring(0, 16)),
        p: common_vendor.t(genConfig.value.lastRunCount)
      } : {}, {
        q: common_vendor.t(genConfig.value.cron),
        r: common_vendor.t(runningGen.value ? "生成中..." : "立即生成一次"),
        s: runningGen.value,
        t: common_vendor.o(runNow)
      }) : {}, {
        v: stats.value
      }, stats.value ? {
        w: common_vendor.o(($event) => batchChangeCount(-1)),
        x: common_vendor.t(batchCount.value),
        y: common_vendor.o(($event) => batchChangeCount(1)),
        z: common_vendor.t(stats.value.placeholderImage),
        A: common_vendor.t(batchRunning.value ? "排队中..." : `批量生成 ${batchCount.value} 道配图`),
        B: batchRunning.value,
        C: common_vendor.o(batchImages)
      } : {}, {
        D: common_vendor.o(onSearch),
        E: keyword.value,
        F: common_vendor.o(($event) => keyword.value = $event.detail.value),
        G: common_vendor.o(onSearch),
        H: common_vendor.f(SOURCE_OPTIONS, (opt, k0, i0) => {
          return {
            a: common_vendor.t(opt.label),
            b: opt.value,
            c: sourceFilter.value === opt.value ? 1 : "",
            d: `按${opt.label}筛选`,
            e: common_vendor.o(($event) => {
              sourceFilter.value = opt.value;
              onFilterChange();
            }, opt.value)
          };
        }),
        I: onlyPlaceholder.value ? 1 : "",
        J: common_vendor.o(($event) => {
          onlyPlaceholder.value = !onlyPlaceholder.value;
          onFilterChange();
        }),
        K: common_vendor.f(list.value, (item, k0, i0) => {
          return common_vendor.e({
            a: item.image
          }, item.image ? {
            b: common_vendor.unref(api_request.resolveAssetUrl)(item.image)
          } : {}, {
            c: common_vendor.t(item.name),
            d: needsImage(item.image) && item.image
          }, needsImage(item.image) && item.image ? {} : needsImage(item.image) ? {} : {}, {
            e: needsImage(item.image),
            f: common_vendor.t(item.description || "暂无描述"),
            g: common_vendor.t(item.source || "-"),
            h: common_vendor.t(item.cookingTime || "?"),
            i: common_vendor.t(item.difficulty || "-"),
            j: needsImage(item.image)
          }, needsImage(item.image) ? {
            k: common_vendor.t(generating.value[item.id] ? "配图中…" : "配图"),
            l: generating.value[item.id] ? 1 : "",
            m: `为${item.name}生成 AI 配图`,
            n: common_vendor.o(($event) => genImage(item), item.id)
          } : {}, {
            o: `删除${item.name}`,
            p: common_vendor.o(($event) => askDelete(item), item.id),
            q: item.id,
            r: `编辑菜谱 ${item.name}`,
            s: common_vendor.o(($event) => goEdit(item), item.id)
          });
        }),
        L: loading.value
      }, loading.value ? {} : !list.value.length && !loading.value ? {} : {}, {
        M: !list.value.length && !loading.value,
        N: common_vendor.o(loadList),
        O: common_vendor.o(($event) => page.value = $event),
        P: common_vendor.p({
          total: total.value,
          ["page-size"]: size,
          ["show-icon"]: true,
          ["hide-if-one-page"]: true,
          modelValue: page.value
        })
      });
    };
  }
});
const MiniProgramPage = /* @__PURE__ */ common_vendor._export_sfc(_sfc_main, [["__scopeId", "data-v-a7a9d3a7"]]);
exports.MiniProgramPage = MiniProgramPage;
