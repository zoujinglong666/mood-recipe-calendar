"use strict";
const common_vendor = require("../../common/vendor.js");
const composables_useNavBar = require("../../composables/useNavBar.js");
const __unplugin_components_0 = () => "../../node-modules/@wot-ui/ui/components/wd-navbar/wd-navbar.js";
if (!Array) {
  const _component_wd_navbar = __unplugin_components_0;
  const _component_layout_default_uni = common_vendor.resolveComponent("layout-default-uni");
  (_component_wd_navbar + _component_layout_default_uni)();
}
if (!Math) {
  RecordEditorForm();
}
const RecordEditorForm = () => "../../components/record/RecordEditorForm.js";
const _sfc_defineComponent = common_vendor.defineComponent({
  __name: "edit",
  setup(__props) {
    common_vendor.onShareAppMessage(() => {
      var _a;
      const pages = getCurrentPages();
      const route2 = ((_a = pages[pages.length - 1]) == null ? void 0 : _a.route) || "";
      return { title: "锅仔 · 按心情帮你决定今天吃什么", path: `/${route2}` };
    });
    common_vendor.onShareTimeline(() => ({ title: "锅仔 · 按心情帮你决定今天吃什么" }));
    const router = common_vendor.useRouter();
    const route = common_vendor.useRoute();
    const recordId = common_vendor.ref(0);
    common_vendor.onLoad((options) => {
      var _a, _b;
      const raw = (options == null ? void 0 : options.id) ?? ((_a = route.query) == null ? void 0 : _a.id) ?? ((_b = route.params) == null ? void 0 : _b.id);
      recordId.value = Number(raw);
    });
    const saved = common_vendor.ref(false);
    function cancel() {
      if (saved.value)
        return;
      router.back();
    }
    function onSaved(_record) {
      saved.value = true;
      router.replace({ name: "timeline" });
    }
    return (_ctx, _cache) => {
      return common_vendor.e({
        a: common_vendor.o(common_vendor.unref(composables_useNavBar.navBack)),
        b: common_vendor.p({
          title: "编辑这顿饭",
          ["left-arrow"]: true,
          ["safe-area-inset-top"]: true,
          ["custom-style"]: "background-color: transparent !important;"
        }),
        c: recordId.value
      }, recordId.value ? {
        d: common_vendor.o(onSaved),
        e: common_vendor.o(cancel),
        f: common_vendor.p({
          ["record-id"]: recordId.value
        })
      } : {
        g: common_vendor.o(($event) => common_vendor.unref(router).replace({
          name: "timeline"
        }))
      });
    };
  }
});
_sfc_defineComponent.__runtimeHooks = 6;
const MiniProgramPage = /* @__PURE__ */ common_vendor._export_sfc(_sfc_defineComponent, [["__scopeId", "data-v-847f49a3"]]);
exports.MiniProgramPage = MiniProgramPage;
