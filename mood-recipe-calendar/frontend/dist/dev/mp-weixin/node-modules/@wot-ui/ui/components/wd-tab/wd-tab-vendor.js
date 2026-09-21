"use strict";
const common_vendor = require("../../../../../common/vendor.js");
const __default__ = {
  name: "wd-tab",
  options: {
    addGlobalClass: true,
    virtualHost: true,
    styleIsolation: "shared"
  }
};
const _sfc_main = /* @__PURE__ */ common_vendor.defineComponent({
  ...__default__,
  props: common_vendor.tabProps,
  setup(__props) {
    const props = __props;
    const { proxy } = common_vendor.getCurrentInstance();
    const { parent: tabs, index } = common_vendor.useParent(common_vendor.TABS_KEY);
    const active = common_vendor.computed(() => {
      return common_vendor.isDef(tabs.value) ? tabs.value.state.activeIndex === index.value : false;
    });
    const painted = common_vendor.ref(active.value);
    const tabBodyStyle = common_vendor.computed(() => {
      const style = {};
      if (!active.value && (!common_vendor.isDef(tabs.value) || !tabs.value.props.animated)) {
        style.display = "none";
      }
      return common_vendor.objToStyle(style);
    });
    const shouldBeRender = common_vendor.computed(() => !props.lazy || painted.value || active.value);
    common_vendor.watch(active, (val) => {
      if (val)
        painted.value = true;
    });
    common_vendor.watch(
      () => props.name,
      (newValue) => {
        if (common_vendor.isDef(newValue) && !common_vendor.isNumber(newValue) && !common_vendor.isString(newValue)) {
          console.error("[wot ui] error(wd-tab): the type of name should be number or string");
          return;
        }
        if (tabs.value) {
          checkName(proxy);
        }
      },
      {
        deep: true,
        immediate: true
      }
    );
    function checkName(self) {
      const { name: myName } = props;
      if (myName === void 0 || myName === null || myName === "") {
        return;
      }
      tabs.value && tabs.value.children.forEach((child) => {
        if (child.$.uid !== self.$.uid && child.name === myName) {
          console.error(`The tab's bound value: ${myName} has been used`);
        }
      });
    }
    return (_ctx, _cache) => {
      return common_vendor.e({
        a: shouldBeRender.value
      }, shouldBeRender.value ? {
        b: common_vendor.n({
          "wd-tab__body--inactive": !active.value
        }),
        c: common_vendor.s(tabBodyStyle.value)
      } : {}, {
        d: common_vendor.n(`wd-tab ${_ctx.customClass}`),
        e: common_vendor.s(_ctx.customStyle)
      });
    };
  }
});
exports._sfc_main = _sfc_main;
