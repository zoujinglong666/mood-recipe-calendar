"use strict";
const common_vendor = require("../../common/vendor.js");
const utils_toast = require("../../utils/toast.js");
const utils_assets = require("../../utils/assets.js");
const composables_useNavBar = require("../../composables/useNavBar.js");
const api_feedback = require("../../api/feedback.js");
const __unplugin_components_0 = () => "../../node-modules/@wot-ui/ui/components/wd-navbar/wd-navbar.js";
if (!Array) {
  const _component_wd_navbar = __unplugin_components_0;
  const _component_layout_default_uni = common_vendor.resolveComponent("layout-default-uni");
  (_component_wd_navbar + _component_layout_default_uni)();
}
const _sfc_main = /* @__PURE__ */ common_vendor.defineComponent({
  __name: "index",
  setup(__props) {
    const categories = ["功能建议", "体验问题", "内容反馈", "其他"];
    const statusLabels = {
      OPEN: "已收到",
      PROCESSING: "处理中",
      RESOLVED: "已完成",
      CLOSED: "已关闭"
    };
    const category = common_vendor.ref("功能建议");
    const content = common_vendor.ref("");
    const contact = common_vendor.ref("");
    const sending = common_vendor.ref(false);
    const history = common_vendor.ref([]);
    const canSubmit = common_vendor.computed(() => content.value.trim().length > 0 && !sending.value);
    function statusLabel(status) {
      return statusLabels[status] || "已收到";
    }
    function formatDate(value) {
      if (!value)
        return "";
      return value.replace("T", " ").slice(0, 16);
    }
    async function loadHistory() {
      try {
        history.value = await api_feedback.fetchMyFeedback();
      } catch {
      }
    }
    async function submit() {
      if (sending.value)
        return;
      if (!content.value.trim()) {
        utils_toast.toast("写下你的建议吧");
        return;
      }
      sending.value = true;
      try {
        await api_feedback.submitFeedback({
          category: category.value,
          content: content.value.trim(),
          contact: contact.value.trim() || void 0
        });
        content.value = "";
        contact.value = "";
        await loadHistory();
        utils_toast.toastSuccess("锅仔收到啦，谢谢你");
      } catch (error) {
        utils_toast.toastError(error, "提交失败");
      } finally {
        sending.value = false;
      }
    }
    common_vendor.onShow(loadHistory);
    return (_ctx, _cache) => {
      return common_vendor.e({
        a: common_vendor.o(common_vendor.unref(composables_useNavBar.navBack)),
        b: common_vendor.p({
          title: "反馈建议",
          ["left-arrow"]: true,
          ["safe-area-inset-top"]: true,
          ["custom-style"]: "background-color: transparent !important;"
        }),
        c: common_vendor.unref(utils_assets.STATIC_BASE_URL) + "/static/guozai/action_08_peek.png",
        d: common_vendor.f(categories, (item, k0, i0) => {
          return {
            a: common_vendor.t(item),
            b: item,
            c: common_vendor.n({
              active: category.value === item
            }),
            d: category.value === item,
            e: common_vendor.o(($event) => category.value = item, item)
          };
        }),
        e: content.value,
        f: common_vendor.o(($event) => content.value = $event.detail.value),
        g: common_vendor.t(content.value.length),
        h: contact.value,
        i: common_vendor.o(($event) => contact.value = $event.detail.value),
        j: common_vendor.t(sending.value ? "正在送给锅仔…" : "把建议交给锅仔"),
        k: !canSubmit.value ? 1 : "",
        l: !canSubmit.value,
        m: common_vendor.o(submit),
        n: history.value.length
      }, history.value.length ? {
        o: common_vendor.t(history.value.length)
      } : {}, {
        p: history.value.length
      }, history.value.length ? {
        q: common_vendor.f(history.value, (item, k0, i0) => {
          return {
            a: common_vendor.t(item.category),
            b: common_vendor.t(formatDate(item.createdAt)),
            c: common_vendor.t(item.content),
            d: common_vendor.t(statusLabel(item.status)),
            e: common_vendor.n(`status-${item.status.toLowerCase()}`),
            f: item.id
          };
        })
      } : {
        r: common_vendor.unref(utils_assets.STATIC_BASE_URL) + "/static/guozai/action_07_empty.png"
      });
    };
  }
});
const MiniProgramPage = /* @__PURE__ */ common_vendor._export_sfc(_sfc_main, [["__scopeId", "data-v-3aa1718d"]]);
exports.MiniProgramPage = MiniProgramPage;
