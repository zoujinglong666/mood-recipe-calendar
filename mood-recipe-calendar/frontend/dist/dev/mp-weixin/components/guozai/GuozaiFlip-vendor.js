"use strict";
const common_vendor = require("../../common/vendor.js");
const utils_assets = require("../../utils/assets.js");
const _sfc_main = /* @__PURE__ */ common_vendor.defineComponent({
  __name: "GuozaiFlip",
  props: {
    visible: { type: Boolean, default: false },
    items: { default: () => [] }
  },
  emits: ["close", "start", "cook"],
  setup(__props, { expose: __expose, emit: __emit }) {
    const props = __props;
    const emit = __emit;
    const DECOR = ["干饭大吉", "香迷糊", "锅气满满", "好胃口", "慢慢炖", "暖暖的", "吃饱饱", "锅仔拿手"];
    const cells = common_vendor.ref(Array.from({ length: 8 }, (_, i) => ({ name: DECOR[i] })));
    const phase = common_vendor.ref("idle");
    const allOpen = common_vendor.ref(false);
    const picked = common_vendor.ref(-1);
    const prize = common_vendor.ref({ name: "" });
    let timers = [];
    common_vendor.watch(() => props.visible, (v) => {
      if (v)
        reset();
      else
        clearTimers();
    });
    function clearTimers() {
      timers.forEach(clearTimeout);
      timers = [];
    }
    function later(fn, ms) {
      timers.push(setTimeout(fn, ms));
    }
    function reset() {
      clearTimers();
      phase.value = "idle";
      allOpen.value = false;
      picked.value = -1;
      buildCells();
    }
    function buildCells() {
      const pool = props.items.filter((item) => item == null ? void 0 : item.name).slice(0, 8);
      cells.value = Array.from({ length: 8 }, (_, i) => pool[i] || { name: DECOR[i] });
    }
    const centerText = common_vendor.computed(() => {
      if (phase.value === "idle")
        return "开翻";
      if (phase.value === "dealing")
        return "洗牌中";
      if (phase.value === "ready")
        return "选一张";
      if (phase.value === "picking")
        return "翻中";
      return "再来一轮";
    });
    const hint = common_vendor.computed(() => {
      if (phase.value === "idle")
        return "按下锅仔，洗牌后选一张";
      if (phase.value === "dealing")
        return "锅仔正在洗牌…";
      if (phase.value === "ready")
        return "选一张牌，翻开今天吃的";
      if (phase.value === "picking")
        return "就是它——";
      return `就吃「${prize.value.name}」！`;
    });
    const waveSeeds = common_vendor.ref([]);
    function rollWaves() {
      waveSeeds.value = Array.from({ length: 8 }, () => ({
        x: Math.round(Math.random() * 34 - 17),
        r: Math.round((Math.random() * 9 - 4.5) * 10) / 10,
        s: 1 + 0.03 + Math.random() * 0.04
      }));
    }
    function waveStyle(index) {
      if (phase.value !== "dealing")
        return {};
      const seed = waveSeeds.value[index];
      if (!seed)
        return { animationDelay: `${index * 70}ms` };
      return {
        animationDelay: `${index * 70}ms`,
        "--wave-x": `${seed.x}rpx`,
        "--wave-r": `${seed.r}deg`,
        "--wave-s": String(seed.s)
      };
    }
    function pressCenter() {
      if (phase.value === "dealing" || phase.value === "ready" || phase.value === "picking")
        return;
      if (phase.value === "revealed") {
        reset();
      }
      rollWaves();
      phase.value = "dealing";
      emit("start");
    }
    function deal(winner) {
      if (phase.value !== "dealing")
        return;
      prize.value = winner;
      later(() => {
        phase.value = "ready";
      }, 1900);
    }
    function fail() {
      if (phase.value !== "dealing")
        return;
      clearTimers();
      phase.value = "idle";
    }
    function pickCard(index) {
      if (phase.value !== "ready")
        return;
      picked.value = index;
      phase.value = "picking";
      later(() => {
        allOpen.value = true;
        phase.value = "revealed";
      }, 1200);
    }
    function isFlipped(index) {
      return allOpen.value || picked.value === index;
    }
    function cardBack(index) {
      return index === picked.value ? prize.value : cells.value[index];
    }
    function cook() {
      if (phase.value === "revealed")
        emit("cook");
    }
    function onMask() {
      if (phase.value !== "dealing" && phase.value !== "picking")
        emit("close");
    }
    __expose({ deal, fail });
    return (_ctx, _cache) => {
      return common_vendor.e({
        a: _ctx.visible
      }, _ctx.visible ? common_vendor.e({
        b: common_vendor.o(onMask),
        c: common_vendor.o(onMask),
        d: common_vendor.t(hint.value),
        e: common_vendor.f(9, (p, k0, i0) => {
          var _a, _b, _c, _d;
          return common_vendor.e({
            a: p - 1 === 4
          }, p - 1 === 4 ? {
            b: common_vendor.unref(utils_assets.STATIC_BASE_URL) + "/static/guozai/action_16_chopsticks.png",
            c: common_vendor.t(centerText.value),
            d: phase.value === "dealing" ? 1 : "",
            e: centerText.value,
            f: common_vendor.o(pressCenter, p)
          } : common_vendor.e({
            g: common_vendor.unref(utils_assets.STATIC_BASE_URL) + "/static/guozai/action_01_bowl.png",
            h: (_a = cardBack(p - 1 > 4 ? p - 2 : p - 1)) == null ? void 0 : _a.image
          }, ((_b = cardBack(p - 1 > 4 ? p - 2 : p - 1)) == null ? void 0 : _b.image) ? {
            i: cardBack(p - 1 > 4 ? p - 2 : p - 1).image
          } : {}, {
            j: common_vendor.t((_c = cardBack(p - 1 > 4 ? p - 2 : p - 1)) == null ? void 0 : _c.name),
            k: isFlipped(p - 1 > 4 ? p - 2 : p - 1) ? 1 : "",
            l: phase.value === "dealing" ? 1 : "",
            m: common_vendor.s(waveStyle(p - 1 > 4 ? p - 2 : p - 1)),
            n: phase.value === "ready" ? "翻开这张牌" : ((_d = cardBack(p - 1 > 4 ? p - 2 : p - 1)) == null ? void 0 : _d.name) || "锅仔牌",
            o: common_vendor.o(($event) => pickCard(p - 1 > 4 ? p - 2 : p - 1), p)
          }), {
            p
          });
        }),
        f: phase.value === "revealed"
      }, phase.value === "revealed" ? common_vendor.e({
        g: prize.value.image
      }, prize.value.image ? {
        h: prize.value.image
      } : {}, {
        i: common_vendor.t(prize.value.name),
        j: common_vendor.o(cook)
      }) : {}) : {});
    };
  }
});
const Component = /* @__PURE__ */ common_vendor._export_sfc(_sfc_main, [["__scopeId", "data-v-60591660"]]);
exports.Component = Component;
