"use strict";
const common_vendor = require("../../common/vendor.js");
const utils_assets = require("../../utils/assets.js");
const _sfc_main = /* @__PURE__ */ common_vendor.defineComponent({
  __name: "MoodPicker",
  props: {
    modelValue: { default: "" },
    showHero: { type: Boolean, default: true },
    confirmText: { default: "" },
    heroHeight: { default: 300 },
    title: { default: "今天的心情" }
  },
  emits: ["update:modelValue", "confirm"],
  setup(__props, { emit: __emit }) {
    const MOOD_OPTIONS = [
      { key: "开心", img: utils_assets.STATIC_BASE_URL + "/static/guozai/mood_01_happy.png", tip: "今天也要闪闪发光呀！" },
      { key: "平静", img: utils_assets.STATIC_BASE_URL + "/static/guozai/mood_02_calm.png", tip: "稳稳的，就是幸福。" },
      { key: "疲惫", img: utils_assets.STATIC_BASE_URL + "/static/guozai/mood_03_tired.png", tip: "累了就歇会儿，我陪你。" },
      { key: "焦虑", img: utils_assets.STATIC_BASE_URL + "/static/guozai/mood_04_anxious.png", tip: "慢慢来，锅仔在呢。" },
      { key: "难过", img: utils_assets.STATIC_BASE_URL + "/static/guozai/mood_05_sad.png", tip: "别难过，有我在。" },
      { key: "嘴馋", img: utils_assets.STATIC_BASE_URL + "/static/guozai/mood_06_hungry.png", tip: "走！咱去吃点好的。" },
      { key: "低落", img: utils_assets.STATIC_BASE_URL + "/static/guozai/mood_07_low.png", tip: "抱抱你，天会亮的。" },
      { key: "想家", img: utils_assets.STATIC_BASE_URL + "/static/guozai/mood_08_homesick.png", tip: "家的味道，最暖。" },
      { key: "期待", img: utils_assets.STATIC_BASE_URL + "/static/guozai/mood_09_excited.png", tip: "前方有好事发生！" },
      { key: "满足", img: utils_assets.STATIC_BASE_URL + "/static/guozai/mood_10_content.png", tip: "这样刚刚好。" },
      { key: "得意", img: utils_assets.STATIC_BASE_URL + "/static/guozai/mood_11_proud.png", tip: "我可太厉害了！" },
      { key: "害羞", img: utils_assets.STATIC_BASE_URL + "/static/guozai/mood_12_shy.png", tip: "被你发现啦～" }
    ];
    const props = __props;
    const emit = __emit;
    const selected = common_vendor.computed({
      get: () => props.modelValue,
      set: (v) => emit("update:modelValue", v)
    });
    const preview = common_vendor.ref("");
    const shownMood = common_vendor.computed(() => {
      const key = preview.value || selected.value;
      return MOOD_OPTIONS.find((m) => m.key === key);
    });
    const heroImg = common_vendor.computed(() => {
      var _a;
      return ((_a = shownMood.value) == null ? void 0 : _a.img) || MOOD_OPTIONS[0].img;
    });
    const heroTip = common_vendor.computed(() => {
      var _a;
      return ((_a = shownMood.value) == null ? void 0 : _a.tip) || MOOD_OPTIONS[0].tip;
    });
    function pick(key) {
      selected.value = key;
      preview.value = key;
      try {
        common_vendor.index.vibrateShort({ type: "light" });
      } catch {
      }
    }
    function onCardTap(key) {
      pick(key);
      if (!props.confirmText)
        return;
    }
    function onConfirm() {
      const mood = MOOD_OPTIONS.find((m) => m.key === selected.value);
      if (mood)
        emit("confirm", mood);
    }
    function heroTap() {
      if (props.confirmText && selected.value)
        onConfirm();
    }
    return (_ctx, _cache) => {
      var _a, _b;
      return common_vendor.e({
        a: common_vendor.t(_ctx.title),
        b: common_vendor.t(selected.value || "未选择"),
        c: selected.value ? 1 : "",
        d: _ctx.showHero
      }, _ctx.showHero ? {
        e: heroImg.value,
        f: _ctx.heroHeight * 0.78 + "rpx",
        g: _ctx.heroHeight * 0.78 + "rpx",
        h: common_vendor.t(((_a = shownMood.value) == null ? void 0 : _a.key) || "开心"),
        i: common_vendor.t(heroTip.value),
        j: _ctx.heroHeight + "rpx",
        k: common_vendor.o(heroTap)
      } : {}, {
        l: common_vendor.f(MOOD_OPTIONS, (m, k0, i0) => {
          return common_vendor.e({
            a: m.img,
            b: common_vendor.t(m.key),
            c: selected.value === m.key
          }, selected.value === m.key ? {} : {}, {
            d: m.key,
            e: selected.value === m.key ? 1 : "",
            f: selected.value === m.key ? 1 : "",
            g: `选择心情：${m.key}${selected.value === m.key ? "，已选择" : ""}`,
            h: common_vendor.o(($event) => onCardTap(m.key), m.key)
          });
        }),
        m: _ctx.confirmText && selected.value
      }, _ctx.confirmText && selected.value ? common_vendor.e({
        n: shownMood.value
      }, shownMood.value ? {
        o: shownMood.value.img
      } : {}, {
        p: common_vendor.t((_b = shownMood.value) == null ? void 0 : _b.key),
        q: common_vendor.t(heroTip.value),
        r: common_vendor.t(_ctx.confirmText),
        s: `${_ctx.confirmText}：${selected.value}`,
        t: common_vendor.o(onConfirm)
      }) : {});
    };
  }
});
const Component = /* @__PURE__ */ common_vendor._export_sfc(_sfc_main, [["__scopeId", "data-v-3aa47bf0"]]);
exports.Component = Component;
