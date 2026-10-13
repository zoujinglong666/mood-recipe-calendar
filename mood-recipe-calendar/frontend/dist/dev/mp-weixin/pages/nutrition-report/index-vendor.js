"use strict";
const common_vendor = require("../../common/vendor.js");
const utils_assets = require("../../utils/assets.js");
const api_nutritionReport = require("../../api/nutritionReport.js");
const utils_toast = require("../../utils/toast.js");
const __unplugin_components_0 = () => "../../node-modules/@wot-ui/ui/components/wd-navbar/wd-navbar.js";
if (!Array) {
  const _component_wd_navbar = __unplugin_components_0;
  const _component_layout_default_uni = common_vendor.resolveComponent("layout-default-uni");
  (_component_wd_navbar + _component_layout_default_uni)();
}
const _sfc_main = /* @__PURE__ */ common_vendor.defineComponent({
  __name: "index",
  setup(__props) {
    const router = common_vendor.useRouter();
    const loading = common_vendor.ref(true);
    const report = common_vendor.ref(null);
    const needMember = common_vendor.ref(false);
    common_vendor.onMounted(async () => {
      try {
        report.value = await api_nutritionReport.fetchNutritionReport();
      } catch (e) {
        if (String((e == null ? void 0 : e.message) || "").includes("会员"))
          needMember.value = true;
        else
          utils_toast.toastError(e, "月报加载失败");
      } finally {
        loading.value = false;
      }
    });
    function goMembership() {
      router.push({ name: "membership" });
    }
    return (_ctx, _cache) => {
      return common_vendor.e({
        a: common_vendor.o(common_vendor.unref(router).back),
        b: common_vendor.p({
          title: "本月营养月报",
          ["left-arrow"]: true,
          ["safe-area-inset-top"]: true,
          ["custom-style"]: "background-color: transparent !important;"
        }),
        c: loading.value
      }, loading.value ? {} : needMember.value ? {
        e: `${common_vendor.unref(utils_assets.STATIC_BASE_URL)}/static/guozai/action_08_peek.png`,
        f: common_vendor.o(goMembership)
      } : !report.value ? {} : common_vendor.e({
        h: common_vendor.t(report.value.days),
        i: common_vendor.t(report.value.meals),
        j: common_vendor.t(report.value.distinctDishes),
        k: common_vendor.t(report.value.longestStreak),
        l: common_vendor.t(report.value.letterTitle),
        m: common_vendor.f(report.value.letterParagraphs, (p, i, i0) => {
          return {
            a: common_vendor.t(p),
            b: i
          };
        }),
        n: report.value.topDishes.length
      }, report.value.topDishes.length ? {
        o: common_vendor.f(report.value.topDishes, (dish, k0, i0) => {
          return {
            a: common_vendor.t(dish.name),
            b: `${Math.round(dish.count / report.value.topDishes[0].count * 100)}%`,
            c: common_vendor.t(dish.count),
            d: dish.name
          };
        })
      } : {}, {
        p: report.value.topMoods.length
      }, report.value.topMoods.length ? {
        q: common_vendor.f(report.value.topMoods, (mood, k0, i0) => {
          return {
            a: common_vendor.t(mood.tag),
            b: common_vendor.t(mood.count),
            c: mood.tag
          };
        })
      } : {}), {
        d: needMember.value,
        g: !report.value
      });
    };
  }
});
const MiniProgramPage = /* @__PURE__ */ common_vendor._export_sfc(_sfc_main, [["__scopeId", "data-v-00832dc4"]]);
exports.MiniProgramPage = MiniProgramPage;
