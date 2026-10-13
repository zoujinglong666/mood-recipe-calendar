"use strict";
const common_vendor = require("../../common/vendor.js");
const utils_assets = require("../../utils/assets.js");
const SEG = 8;
const _sfc_main = /* @__PURE__ */ common_vendor.defineComponent({
  __name: "GuozaiWheel",
  props: {
    visible: { type: Boolean, default: false },
    items: { default: () => [] }
  },
  emits: ["close", "spin", "result"],
  setup(__props, { expose: __expose, emit: __emit }) {
    const props = __props;
    const emit = __emit;
    const SEG_DEG = 360 / SEG;
    const DECOR = [
      { name: "干饭大吉" },
      { name: "香迷糊" },
      { name: "锅气满满" },
      { name: "好胃口" },
      { name: "慢慢炖" },
      { name: "暖暖的" },
      { name: "吃饱饱" },
      { name: "锅仔拿手" }
    ];
    const slots = common_vendor.ref(Array.from({ length: SEG }, (_, i) => ({ ...DECOR[i % DECOR.length] })));
    const rotation = common_vendor.ref(0);
    const phase = common_vendor.ref("idle");
    const resultName = common_vendor.ref("");
    const resultImage = common_vendor.ref("");
    common_vendor.watch(() => props.visible, (v) => {
      if (v) {
        phase.value = "idle";
        resultName.value = "";
        buildSegments();
      }
    });
    function buildSegments() {
      const pool = props.items.filter((item) => item == null ? void 0 : item.name).slice(0, SEG);
      slots.value = Array.from({ length: SEG }, (_, i) => pool[i] || { ...DECOR[i % DECOR.length] });
    }
    const discBg = common_vendor.computed(() => {
      const stops = Array.from({ length: SEG }, (_, i) => {
        const color = i % 2 ? "#FFF3E6" : "#FFDDBF";
        return `${color} ${SEG_DEG * i}deg ${SEG_DEG * (i + 1)}deg`;
      });
      return `conic-gradient(${stops.join(", ")})`;
    });
    const hint = common_vendor.computed(() => {
      if (phase.value === "waiting")
        return "锅仔正在锅里翻找…";
      if (phase.value === "spinning")
        return "转起来了——";
      if (phase.value === "landed")
        return resultName.value ? `就吃「${resultName.value}」！` : "就吃它！";
      return "拍一下锅仔，转一道今天的菜";
    });
    const hubText = common_vendor.computed(() => phase.value === "landed" ? "再转" : "转");
    function segAngle(i) {
      return SEG_DEG * i + SEG_DEG / 2;
    }
    function tapHub() {
      if (phase.value !== "idle" && phase.value !== "landed")
        return;
      phase.value = "waiting";
      emit("spin");
    }
    function land(name, image) {
      if (phase.value !== "waiting")
        return;
      resultName.value = name;
      resultImage.value = image || "";
      const slot = Math.floor(Math.random() * SEG);
      const next = [...slots.value];
      next[slot] = { name, image: image || next[slot].image };
      slots.value = next;
      phase.value = "spinning";
      const jitter = Math.random() * (SEG_DEG * 0.7) - SEG_DEG * 0.35;
      const base = Math.ceil(rotation.value / 360) * 360;
      rotation.value = base + 360 * 5 + (360 - segAngle(slot)) + jitter;
      if (landTimer)
        clearTimeout(landTimer);
      landTimer = setTimeout(() => {
        if (phase.value === "spinning")
          finish();
      }, 4800);
    }
    let landTimer;
    function onSpinEnd() {
      if (phase.value !== "spinning")
        return;
      if (landTimer)
        clearTimeout(landTimer);
      finish();
    }
    function finish() {
      phase.value = "landed";
      emit("result", resultName.value);
    }
    function fail() {
      if (phase.value === "waiting")
        phase.value = "idle";
    }
    function onMask() {
      if (phase.value === "idle" || phase.value === "landed")
        emit("close");
    }
    __expose({ land, fail });
    return (_ctx, _cache) => {
      return common_vendor.e({
        a: _ctx.visible
      }, _ctx.visible ? common_vendor.e({
        b: common_vendor.o(onMask),
        c: common_vendor.o(onMask),
        d: common_vendor.t(hint.value),
        e: common_vendor.f(16, (i, k0, i0) => {
          return {
            a: i,
            b: i % 2 === 0 ? 1 : "",
            c: `rotate(${(i - 1) * 22.5}deg) translateY(-318rpx)`
          };
        }),
        f: `translate(-50%, -50%) rotate(${rotation.value}deg)`,
        g: common_vendor.f(slots.value, (slot, i, i0) => {
          return common_vendor.e({
            a: slot.image
          }, slot.image ? {
            b: slot.image
          } : {}, {
            c: common_vendor.t(slot.name),
            d: i,
            e: `rotate(${segAngle(i)}deg)`
          });
        }),
        h: phase.value === "landed" ? 1 : "",
        i: discBg.value,
        j: `rotate(${rotation.value}deg)`,
        k: common_vendor.o(onSpinEnd),
        l: common_vendor.unref(utils_assets.STATIC_BASE_URL) + "/static/guozai/action_01_bowl.png",
        m: common_vendor.t(hubText.value),
        n: phase.value === "waiting" ? 1 : "",
        o: phase.value === "landed" ? "再转一次" : "转一转换道菜",
        p: common_vendor.o(tapHub),
        q: phase.value === "landed" && resultName.value
      }, phase.value === "landed" && resultName.value ? common_vendor.e({
        r: resultImage.value
      }, resultImage.value ? {
        s: resultImage.value
      } : {}, {
        t: common_vendor.t(resultName.value),
        v: common_vendor.o(($event) => emit("close"))
      }) : {}) : {});
    };
  }
});
const Component = /* @__PURE__ */ common_vendor._export_sfc(_sfc_main, [["__scopeId", "data-v-e58ef490"]]);
exports.Component = Component;
