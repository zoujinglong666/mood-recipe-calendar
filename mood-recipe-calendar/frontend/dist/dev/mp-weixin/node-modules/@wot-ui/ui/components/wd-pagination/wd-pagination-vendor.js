"use strict";
const common_vendor = require("../../../../../common/vendor.js");
if (!Math) {
  wdButton();
}
const wdButton = () => "../wd-button/wd-button.js";
const __default__ = {
  name: "wd-pagination",
  options: {
    virtualHost: true,
    addGlobalClass: true,
    styleIsolation: "shared"
  }
};
const _sfc_main = /* @__PURE__ */ common_vendor.defineComponent({
  ...__default__,
  props: common_vendor.paginationProps,
  emits: ["change", "update:modelValue"],
  setup(__props, { emit: __emit }) {
    const { translate } = common_vendor.useTranslate("pagination");
    const props = __props;
    const emit = __emit;
    const totalPageNum = common_vendor.ref(0);
    common_vendor.watch(
      () => props.totalPage,
      (newValue) => {
        if (!totalPageNum.value && newValue) {
          totalPageNum.value = newValue;
        }
      },
      { immediate: true, deep: true }
    );
    common_vendor.watch(
      () => props.total,
      () => {
        updateTotalPage();
      },
      { immediate: true, deep: true }
    );
    function add() {
      const { modelValue } = props;
      if (modelValue > totalPageNum.value - 1) {
        return;
      }
      emit("change", { value: modelValue + 1 });
      emit("update:modelValue", modelValue + 1);
    }
    function sub() {
      const { modelValue } = props;
      if (modelValue < 2) {
        return;
      }
      emit("change", { value: modelValue - 1 });
      emit("update:modelValue", modelValue - 1);
    }
    function updateTotalPage() {
      const { total, pageSize } = props;
      totalPageNum.value = Math.ceil(total / pageSize);
    }
    return (_ctx, _cache) => {
      return common_vendor.e({
        a: !(_ctx.hideIfOnePage && totalPageNum.value === 1)
      }, !(_ctx.hideIfOnePage && totalPageNum.value === 1) ? common_vendor.e({
        b: common_vendor.o(sub),
        c: common_vendor.p({
          plain: _ctx.modelValue > 1,
          type: "info",
          variant: _ctx.buttonVariant,
          size: "small",
          disabled: _ctx.modelValue <= 1,
          ["custom-class"]: `wd-pagination__nav`,
          icon: _ctx.showIcon ? "left" : "",
          text: _ctx.showIcon ? "" : _ctx.prevText || common_vendor.unref(translate)("prev")
        }),
        d: common_vendor.r("prev", {
          modelValue: _ctx.modelValue,
          totalPageNum: totalPageNum.value,
          total: _ctx.total,
          pageSize: _ctx.pageSize
        }),
        e: common_vendor.t(_ctx.modelValue),
        f: common_vendor.t(totalPageNum.value),
        g: common_vendor.r("size", {
          modelValue: _ctx.modelValue,
          totalPageNum: totalPageNum.value,
          total: _ctx.total,
          pageSize: _ctx.pageSize
        }),
        h: common_vendor.o(add),
        i: common_vendor.p({
          plain: _ctx.modelValue < totalPageNum.value,
          type: "primary",
          variant: _ctx.buttonVariant,
          size: "small",
          disabled: _ctx.modelValue >= totalPageNum.value,
          ["custom-class"]: `wd-pagination__nav`,
          icon: _ctx.showIcon ? "right" : "",
          text: _ctx.showIcon ? "" : _ctx.nextText || common_vendor.unref(translate)("next")
        }),
        j: common_vendor.r("next", {
          modelValue: _ctx.modelValue,
          totalPageNum: totalPageNum.value,
          total: _ctx.total,
          pageSize: _ctx.pageSize
        }),
        k: _ctx.showMessage
      }, _ctx.showMessage ? common_vendor.e({
        l: common_vendor.t(common_vendor.unref(translate)("page", _ctx.modelValue)),
        m: _ctx.total
      }, _ctx.total ? {
        n: common_vendor.t(common_vendor.unref(translate)("total", _ctx.total))
      } : {}, {
        o: common_vendor.t(common_vendor.unref(translate)("size", _ctx.pageSize)),
        p: common_vendor.r("message", {
          modelValue: _ctx.modelValue,
          totalPageNum: totalPageNum.value,
          total: _ctx.total,
          pageSize: _ctx.pageSize
        })
      }) : {}, {
        q: common_vendor.n(`wd-pagination ${_ctx.customClass}`),
        r: common_vendor.s(_ctx.customStyle)
      }) : {});
    };
  }
});
exports._sfc_main = _sfc_main;
