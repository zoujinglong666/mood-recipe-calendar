"use strict";
const common_vendor = require("../../common/vendor.js");
const utils_assets = require("../../utils/assets.js");
const _sfc_main = /* @__PURE__ */ common_vendor.defineComponent({
  __name: "GuozaiNine",
  props: {
    visible: { type: Boolean, default: false },
    items: { default: () => [] }
  },
  emits: ["close", "start", "result", "cook"],
  setup(__props, { expose: __expose, emit: __emit }) {
    const props = __props;
    const emit = __emit;
    const ORDER = [0, 1, 2, 5, 8, 7, 6, 3];
    const DECOR = ["干饭大吉", "香迷糊", "锅气满满", "好胃口", "慢慢炖", "暖暖的", "吃饱饱", "锅仔拿手"];
    const cells = common_vendor.ref(Array.from({ length: 8 }, (_, i) => ({ name: DECOR[i] })));
    const activePos = common_vendor.ref(-1);
    const phase = common_vendor.ref("idle");
    const resultIndex = common_vendor.ref(-1);
    common_vendor.watch(() => props.visible, (v) => {
      if (v) {
        phase.value = "idle";
        resultIndex.value = -1;
        activePos.value = -1;
        buildCells();
      }
    });
    function buildCells() {
      const pool = props.items.filter((item) => item == null ? void 0 : item.name).slice(0, 8);
      cells.value = Array.from({ length: 8 }, (_, i) => pool[i] || { name: DECOR[i] });
    }
    const centerText = common_vendor.computed(() => phase.value === "landed" ? "再来" : "开转");
    const hint = common_vendor.computed(() => {
      var _a;
      if (phase.value === "running")
        return "锅仔在九宫格里翻找…";
      if (phase.value === "landed" && resultIndex.value >= 0)
        return `就吃「${((_a = cells.value[resultIndex.value]) == null ? void 0 : _a.name) || ""}」！`;
      return "按下锅仔，翻出一道今天的菜";
    });
    function isLit(p) {
      const orderIdx = ORDER.indexOf(p);
      if (orderIdx < 0)
        return null;
      if (phase.value === "landed" && resultIndex.value === orderIdx)
        return "win";
      if (phase.value === "running" && activePos.value === orderIdx)
        return "run";
      return null;
    }
    function pressCenter() {
      if (phase.value !== "idle" && phase.value !== "landed")
        return;
      phase.value = "running";
      emit("start");
    }
    function land(target) {
      if (phase.value !== "running")
        return;
      const start = Math.max(0, activePos.value);
      const delta = (target - start + 8) % 8;
      const total = 24 + delta;
      let step = 0;
      const tick = () => {
        activePos.value = (start + step) % 8;
        step += 1;
        if (step > total) {
          phase.value = "landed";
          resultIndex.value = target;
          emit("result", target);
          return;
        }
        const slowdown = Math.max(0, step - (total - 12));
        const delay = slowdown > 0 ? 75 + slowdown / 12 * 330 : 72;
        setTimeout(tick, delay);
      };
      tick();
    }
    function onMask() {
      if (phase.value !== "running")
        emit("close");
    }
    function cook() {
      if (resultIndex.value >= 0)
        emit("cook", resultIndex.value);
    }
    __expose({ land });
    return (_ctx, _cache) => {
      var _a, _b, _c;
      return common_vendor.e({
        a: _ctx.visible
      }, _ctx.visible ? common_vendor.e({
        b: common_vendor.o(onMask),
        c: common_vendor.o(onMask),
        d: common_vendor.t(hint.value),
        e: common_vendor.f(9, (p, k0, i0) => {
          var _a2, _b2, _c2, _d;
          return common_vendor.e({
            a: p - 1 === 4
          }, p - 1 === 4 ? {
            b: common_vendor.unref(utils_assets.STATIC_BASE_URL) + "/static/guozai/action_16_chopsticks.png",
            c: common_vendor.t(centerText.value),
            d: phase.value === "running" ? 1 : "",
            e: centerText.value,
            f: common_vendor.o(pressCenter, p)
          } : common_vendor.e({
            g: (_a2 = cells.value[ORDER.indexOf(p - 1)]) == null ? void 0 : _a2.image
          }, ((_b2 = cells.value[ORDER.indexOf(p - 1)]) == null ? void 0 : _b2.image) ? {
            h: cells.value[ORDER.indexOf(p - 1)].image
          } : {}, {
            i: common_vendor.t((_c2 = cells.value[ORDER.indexOf(p - 1)]) == null ? void 0 : _c2.name),
            j: isLit(p - 1) === "run" ? 1 : "",
            k: isLit(p - 1) === "win" ? 1 : "",
            l: phase.value === "running" && !isLit(p - 1) ? 1 : "",
            m: (_d = cells.value[ORDER.indexOf(p - 1)]) == null ? void 0 : _d.name
          }), {
            n: p
          });
        }),
        f: phase.value === "landed" && resultIndex.value >= 0
      }, phase.value === "landed" && resultIndex.value >= 0 ? common_vendor.e({
        g: (_a = cells.value[resultIndex.value]) == null ? void 0 : _a.image
      }, ((_b = cells.value[resultIndex.value]) == null ? void 0 : _b.image) ? {
        h: cells.value[resultIndex.value].image
      } : {}, {
        i: common_vendor.t((_c = cells.value[resultIndex.value]) == null ? void 0 : _c.name),
        j: common_vendor.o(cook)
      }) : {}) : {});
    };
  }
});
const Component = /* @__PURE__ */ common_vendor._export_sfc(_sfc_main, [["__scopeId", "data-v-ead2bddb"]]);
exports.Component = Component;
