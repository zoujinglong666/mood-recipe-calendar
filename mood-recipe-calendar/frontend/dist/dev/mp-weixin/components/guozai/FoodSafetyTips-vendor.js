"use strict";
const common_vendor = require("../../common/vendor.js");
const api_foodSafety = require("../../api/foodSafety.js");
const _sfc_main = /* @__PURE__ */ common_vendor.defineComponent({
  __name: "FoodSafetyTips",
  props: {
    ingredients: {}
  },
  setup(__props) {
    const props = __props;
    const tips = common_vendor.ref([]);
    const loading = common_vendor.ref(false);
    let seq = 0;
    const TYPE_META = {
      COOK: { badge: "烹", cls: "tip--cook" },
      AVOID: { badge: "慎", cls: "tip--avoid" },
      COMBO: { badge: "搭", cls: "tip--combo" },
      PICK: { badge: "挑", cls: "tip--pick" }
    };
    common_vendor.watch(() => props.ingredients, (list) => {
      const names = (list || []).map((s) => String(s).trim()).filter(Boolean);
      if (!names.length) {
        tips.value = [];
        return;
      }
      const run = ++seq;
      loading.value = true;
      api_foodSafety.fetchFoodTips(names).then((result) => {
        if (run !== seq)
          return;
        tips.value = result.filter((t) => t.type !== "PICK");
      }).catch(() => {
        if (run === seq)
          tips.value = [];
      }).finally(() => {
        if (run === seq)
          loading.value = false;
      });
    }, { immediate: true, deep: false });
    return (_ctx, _cache) => {
      return common_vendor.e({
        a: tips.value.length
      }, tips.value.length ? {
        b: common_vendor.f(tips.value, (tip, idx, i0) => {
          var _a, _b;
          return {
            a: common_vendor.t(((_a = TYPE_META[tip.type]) == null ? void 0 : _a.badge) || "提"),
            b: common_vendor.t(tip.name),
            c: common_vendor.t(tip.text),
            d: tip.type + tip.name + idx,
            e: common_vendor.n((_b = TYPE_META[tip.type]) == null ? void 0 : _b.cls)
          };
        })
      } : {});
    };
  }
});
const Component = /* @__PURE__ */ common_vendor._export_sfc(_sfc_main, [["__scopeId", "data-v-4f864cbd"]]);
exports.Component = Component;
