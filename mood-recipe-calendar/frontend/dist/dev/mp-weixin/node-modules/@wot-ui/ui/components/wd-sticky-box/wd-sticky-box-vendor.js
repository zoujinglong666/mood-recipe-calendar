"use strict";
const common_vendor = require("../../../../../common/vendor.js");
if (!Math) {
  wdResize();
}
const wdResize = () => "../wd-resize/wd-resize.js";
const __default__ = {
  name: "wd-sticky-box",
  options: {
    addGlobalClass: true,
    // virtualHost: true,
    styleIsolation: "shared"
  }
};
const _sfc_main = /* @__PURE__ */ common_vendor.defineComponent({
  ...__default__,
  props: common_vendor.baseProps,
  setup(__props) {
    const props = __props;
    const styckyBoxId = common_vendor.ref(`wd-sticky-box${common_vendor.uuid()}`);
    const observerMap = common_vendor.ref(/* @__PURE__ */ new Map());
    const boxStyle = common_vendor.reactive({
      height: 0,
      width: 0
    });
    const { proxy } = common_vendor.getCurrentInstance();
    const { children: stickyList, linkChildren } = common_vendor.useChildren(common_vendor.STICKY_BOX_KEY);
    linkChildren({
      boxStyle,
      observerForChild
    });
    common_vendor.onBeforeMount(() => {
      observerMap.value = /* @__PURE__ */ new Map();
    });
    function handleResize(detail) {
      boxStyle.width = detail.width;
      boxStyle.height = detail.height;
      const temp = observerMap.value;
      observerMap.value = /* @__PURE__ */ new Map();
      for (const [uid] of temp) {
        const child = stickyList.find((sticky) => {
          return sticky.$.uid === uid;
        });
        observerForChild(child);
      }
      temp.forEach((observer) => {
        observer.disconnect();
      });
      temp.clear();
    }
    function deleteObserver(child) {
      const observer = observerMap.value.get(child.$.uid);
      if (!observer)
        return;
      observer.disconnect();
      observerMap.value.delete(child.$.uid);
    }
    function createObserver(child) {
      const observer = common_vendor.index.createIntersectionObserver(proxy, { thresholds: [0, 0.5] });
      observerMap.value.set(child.$.uid, observer);
      return observer;
    }
    function observerForChild(child) {
      deleteObserver(child);
      const observer = createObserver(child);
      const exposed = child.$.exposed;
      let offset = exposed.stickyState.height + exposed.offsetTop;
      if (boxStyle.height <= exposed.stickyState.height) {
        exposed.setPosition(false, "absolute", 0);
      }
      observer.relativeToViewport({ top: -offset }).observe(`#${styckyBoxId.value}`, (result) => {
        handleRelativeTo(exposed, result);
      });
      common_vendor.getRect(`#${styckyBoxId.value}`, false, proxy).then((res) => {
        if (Number(res.bottom) <= offset)
          handleRelativeTo(exposed, { boundingClientRect: res });
      }).catch((res) => {
        console.log(res);
      });
    }
    function handleRelativeTo(exposed, { boundingClientRect }) {
      let childOffsetTop = exposed.offsetTop;
      const offset = exposed.stickyState.height + childOffsetTop;
      let isAbsolute = boundingClientRect.bottom <= offset;
      if (isAbsolute) {
        exposed.setPosition(true, "absolute", boundingClientRect.height - exposed.stickyState.height);
      } else if (boundingClientRect.top <= offset && !isAbsolute) {
        if (exposed.stickyState.state === "normal")
          return;
        exposed.setPosition(false, "fixed", childOffsetTop);
      }
    }
    return (_ctx, _cache) => {
      return {
        a: common_vendor.o(handleResize),
        b: common_vendor.n(`wd-sticky-box ${props.customClass}`),
        c: common_vendor.s(_ctx.customStyle),
        d: styckyBoxId.value
      };
    };
  }
});
exports._sfc_main = _sfc_main;
