"use strict";
const common_vendor = require("../../common/vendor.js");
const _sfc_main = /* @__PURE__ */ common_vendor.defineComponent({
  ...{ inheritAttrs: false },
  __name: "GuozaiImage",
  props: {
    src: { default: "" },
    fallbacks: { default: () => [] },
    mode: { default: "aspectFill" },
    placeholderText: { default: "🍲" },
    placeholderClass: { default: "" },
    ariaLabel: { default: "" }
  },
  emits: ["click"],
  setup(__props, { emit: __emit }) {
    const props = __props;
    const emit = __emit;
    const index = common_vendor.ref(0);
    const chain = common_vendor.computed(
      () => [props.src, ...props.fallbacks].filter((s) => !!s && s.trim().length > 0)
    );
    const displaySrc = common_vendor.computed(
      () => index.value < chain.value.length ? chain.value[index.value] : ""
    );
    function onError() {
      if (index.value < chain.value.length - 1) {
        index.value += 1;
      } else {
        index.value = chain.value.length;
      }
    }
    function onLoad() {
    }
    common_vendor.watch(() => props.src, () => {
      index.value = 0;
    });
    return (_ctx, _cache) => {
      return common_vendor.e({
        a: displaySrc.value
      }, displaySrc.value ? {
        b: displaySrc.value,
        c: _ctx.mode,
        d: _ctx.ariaLabel,
        e: common_vendor.o(onError),
        f: common_vendor.o(onLoad),
        g: common_vendor.o(($event) => emit("click", $event))
      } : {
        h: common_vendor.t(_ctx.placeholderText),
        i: common_vendor.n(_ctx.placeholderClass),
        j: _ctx.ariaLabel,
        k: common_vendor.o(($event) => emit("click", $event))
      });
    };
  }
});
const Component = /* @__PURE__ */ common_vendor._export_sfc(_sfc_main, [["__scopeId", "data-v-88378a1c"]]);
exports.Component = Component;
