"use strict";
const common_vendor = require("../../common/vendor.js");
const utils_assets = require("../../utils/assets.js");
const _sfc_main = /* @__PURE__ */ common_vendor.defineComponent({
  __name: "LoadingState",
  props: {
    text: { default: "锅仔正在准备..." }
  },
  setup(__props) {
    return (_ctx, _cache) => {
      return {
        a: common_vendor.unref(utils_assets.STATIC_BASE_URL) + "/static/guozai/state_02_loading.png",
        b: common_vendor.t(_ctx.text)
      };
    };
  }
});
const Component = /* @__PURE__ */ common_vendor._export_sfc(_sfc_main, [["__scopeId", "data-v-a8e33f4d"]]);
exports.Component = Component;
