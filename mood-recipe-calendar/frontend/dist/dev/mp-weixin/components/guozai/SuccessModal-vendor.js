"use strict";
const common_vendor = require("../../common/vendor.js");
const utils_assets = require("../../utils/assets.js");
const _sfc_main = /* @__PURE__ */ common_vendor.defineComponent({
  __name: "SuccessModal",
  props: {
    visible: { type: Boolean },
    title: { default: "记录成功！" },
    subtitle: { default: "今天也好好吃饭了呢" },
    confirmText: { default: "好的" },
    secondaryText: { default: "" }
  },
  emits: ["confirm", "secondary"],
  setup(__props, { emit: __emit }) {
    const props = __props;
    const emit = __emit;
    function handleConfirm() {
      emit("confirm");
    }
    function handleSecondary() {
      emit("secondary");
    }
    const GZ_CELEBRATE_URL = `${utils_assets.STATIC_BASE_URL}/static/guozai/action_09_celebrate.png`;
    const imgStatus = common_vendor.ref("待加载");
    const imgDetail = common_vendor.ref("");
    function onGuozaiLoad(e) {
      const detail = (e == null ? void 0 : e.detail) || {};
      imgStatus.value = "已加载";
      imgDetail.value = `${detail.width || "?"}×${detail.height || "?"}`;
      console.log("[SuccessModal][锅仔图] 加载成功", {
        url: GZ_CELEBRATE_URL,
        natural: `${detail.width}×${detail.height}`,
        detail
      });
      common_vendor.index.createSelectorQuery().select(".gz-modal__guozai").boundingClientRect((rect) => {
        console.log("[SuccessModal][锅仔图] 渲染尺寸", rect);
        if (!rect || !rect.width)
          imgDetail.value += " · 渲染尺寸为 0（CSS/遮挡问题）";
      }).exec();
    }
    function onGuozaiError(e) {
      var _a, _b, _c;
      const errMsg = ((_a = e == null ? void 0 : e.detail) == null ? void 0 : _a.errMsg) || ((_b = e == null ? void 0 : e.detail) == null ? void 0 : _b.errno) || JSON.stringify((e == null ? void 0 : e.detail) || {});
      imgStatus.value = "加载失败";
      imgDetail.value = String(errMsg);
      console.error("[SuccessModal][锅仔图] 加载失败", {
        url: GZ_CELEBRATE_URL,
        errMsg: (_c = e == null ? void 0 : e.detail) == null ? void 0 : _c.errMsg,
        detail: e == null ? void 0 : e.detail,
        event: e
      });
    }
    common_vendor.watch(() => props.visible, (v) => {
      if (!v)
        return;
      imgStatus.value = "待加载";
      imgDetail.value = "";
      console.log("[SuccessModal][锅仔图] 弹窗打开，即将请求:", GZ_CELEBRATE_URL);
    });
    return (_ctx, _cache) => {
      return common_vendor.e({
        a: _ctx.visible
      }, _ctx.visible ? common_vendor.e({
        b: GZ_CELEBRATE_URL,
        c: common_vendor.o(onGuozaiLoad),
        d: common_vendor.o(onGuozaiError),
        e: imgStatus.value === "加载失败"
      }, imgStatus.value === "加载失败" ? {
        f: common_vendor.t(imgStatus.value),
        g: common_vendor.t(imgDetail.value)
      } : {}, {
        h: common_vendor.t(_ctx.title),
        i: common_vendor.t(_ctx.subtitle),
        j: _ctx.$slots.default
      }, _ctx.$slots.default ? {} : {}, {
        k: _ctx.secondaryText
      }, _ctx.secondaryText ? {
        l: common_vendor.t(_ctx.secondaryText),
        m: _ctx.secondaryText,
        n: common_vendor.o(handleSecondary)
      } : {}, {
        o: common_vendor.t(_ctx.confirmText),
        p: _ctx.confirmText,
        q: common_vendor.o(handleConfirm),
        r: _ctx.secondaryText ? 1 : "",
        s: common_vendor.o(() => {
        }),
        t: common_vendor.o(handleConfirm)
      }) : {});
    };
  }
});
const Component = /* @__PURE__ */ common_vendor._export_sfc(_sfc_main, [["__scopeId", "data-v-ca257a88"]]);
exports.Component = Component;
