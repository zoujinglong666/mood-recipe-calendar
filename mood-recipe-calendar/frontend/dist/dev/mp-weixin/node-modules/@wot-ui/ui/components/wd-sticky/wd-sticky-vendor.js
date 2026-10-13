"use strict";
const common_vendor = require("../../../../../common/vendor.js");
if (!Math) {
  wdResize();
}
const wdResize = () => "../wd-resize/wd-resize.js";
const __default__ = {
  name: "wd-sticky",
  options: {
    addGlobalClass: true,
    virtualHost: true,
    styleIsolation: "shared"
  }
};
const _sfc_main = /* @__PURE__ */ common_vendor.defineComponent({
  ...__default__,
  props: common_vendor.stickyProps,
  setup(__props, { expose: __expose }) {
    const props = __props;
    const styckyId = common_vendor.ref(`wd-sticky${common_vendor.uuid()}`);
    const observerList = common_vendor.ref([]);
    const stickyState = common_vendor.reactive({
      position: "absolute",
      boxLeaved: false,
      top: 0,
      height: 0,
      width: 0,
      state: ""
    });
    const { parent: stickyBox } = common_vendor.useParent(common_vendor.STICKY_BOX_KEY);
    const { proxy } = common_vendor.getCurrentInstance();
    const rootStyle = common_vendor.computed(() => {
      const style = {
        "z-index": props.zIndex,
        height: common_vendor.addUnit(stickyState.height),
        width: common_vendor.addUnit(stickyState.width)
      };
      if (!stickyState.boxLeaved) {
        style["position"] = "relative";
      }
      return `${common_vendor.objToStyle(style)}${props.customStyle}`;
    });
    const stickyStyle = common_vendor.computed(() => {
      const style = {
        "z-index": props.zIndex,
        height: common_vendor.addUnit(stickyState.height),
        width: common_vendor.addUnit(stickyState.width)
      };
      if (!stickyState.boxLeaved) {
        style["position"] = "relative";
      }
      return `${common_vendor.objToStyle(style)}`;
    });
    const containerStyle = common_vendor.computed(() => {
      const style = {
        position: stickyState.position,
        top: common_vendor.addUnit(stickyState.top)
      };
      return common_vendor.objToStyle(style);
    });
    const innerOffsetTop = common_vendor.computed(() => {
      let top = 0;
      return top + props.offsetTop;
    });
    function clearObserver() {
      while (observerList.value.length !== 0) {
        observerList.value.pop().disconnect();
      }
    }
    function createObserver() {
      const observer = common_vendor.index.createIntersectionObserver(proxy, { thresholds: [0, 0.5] });
      observerList.value.push(observer);
      return observer;
    }
    async function handleResize(detail) {
      stickyState.width = detail.width;
      stickyState.height = detail.height;
      await common_vendor.pause();
      observerContentScroll();
      if (!stickyBox.value || !stickyBox.value.observerForChild)
        return;
      stickyBox.value.observerForChild(proxy);
    }
    function observerContentScroll() {
      if (stickyState.height === 0 && stickyState.width === 0)
        return;
      const offset = innerOffsetTop.value + stickyState.height;
      clearObserver();
      createObserver().relativeToViewport({
        top: -offset
      }).observe(`#${styckyId.value}`, (result) => {
        handleRelativeTo(result);
      });
      common_vendor.getRect(`#${styckyId.value}`, false, proxy).then((res) => {
        if (Number(res.bottom) <= offset)
          handleRelativeTo({ boundingClientRect: res });
      });
    }
    function handleRelativeTo({ boundingClientRect }) {
      if (stickyBox.value && stickyBox.value.boxStyle && stickyState.height >= stickyBox.value.boxStyle.height) {
        stickyState.position = "absolute";
        stickyState.top = 0;
        return;
      }
      let isStycky = boundingClientRect.top <= innerOffsetTop.value;
      if (isStycky) {
        stickyState.state = "sticky";
        stickyState.boxLeaved = false;
        stickyState.position = "fixed";
        stickyState.top = innerOffsetTop.value;
      } else {
        stickyState.state = "normal";
        stickyState.boxLeaved = false;
        stickyState.position = "absolute";
        stickyState.top = 0;
      }
    }
    function setPosition(boxLeaved, position, top) {
      stickyState.boxLeaved = boxLeaved;
      stickyState.position = position;
      stickyState.top = top;
    }
    __expose({
      setPosition,
      stickyState,
      offsetTop: props.offsetTop
    });
    return (_ctx, _cache) => {
      return {
        a: common_vendor.o(handleResize),
        b: common_vendor.p({
          ["custom-style"]: "display: inline-block;"
        }),
        c: common_vendor.s(containerStyle.value),
        d: common_vendor.n(`wd-sticky ${_ctx.customClass}`),
        e: common_vendor.s(stickyStyle.value),
        f: styckyId.value,
        g: common_vendor.s(`${rootStyle.value};display: inline-block;`)
      };
    };
  }
});
exports._sfc_main = _sfc_main;
