"use strict";
const common_vendor = require("../../../../../common/vendor.js");
if (!Math) {
  (wdLoading + wdIcon)();
}
const wdIcon = () => "../wd-icon/wd-icon.js";
const wdLoading = () => "../wd-loading/wd-loading.js";
const __default__ = {
  name: "wd-button",
  options: {
    addGlobalClass: true,
    virtualHost: true,
    styleIsolation: "shared"
  }
};
const _sfc_main = /* @__PURE__ */ common_vendor.defineComponent({
  ...__default__,
  props: common_vendor.buttonProps,
  emits: ["click", "getuserinfo", "contact", "getphonenumber", "getrealtimephonenumber", "error", "launchapp", "opensetting", "chooseavatar", "agreeprivacyauthorization"],
  setup(__props, { emit: __emit }) {
    const slots = common_vendor.useSlots();
    const props = __props;
    const globalConfig = common_vendor.useGlobalConfig();
    const effectiveSize = common_vendor.computed(() => {
      var _a;
      return props.size || ((_a = globalConfig.value.button) == null ? void 0 : _a.size) || "medium";
    });
    const effectiveVariant = common_vendor.computed(() => {
      var _a;
      return props.variant || ((_a = globalConfig.value.button) == null ? void 0 : _a.variant) || "base";
    });
    const effectiveType = common_vendor.computed(() => {
      var _a;
      return props.type || ((_a = globalConfig.value.button) == null ? void 0 : _a.type) || "primary";
    });
    const effectiveRound = common_vendor.computed(() => {
      var _a, _b;
      if (common_vendor.isUndefined(props.round)) {
        return common_vendor.isUndefined((_a = globalConfig.value.button) == null ? void 0 : _a.round) ? false : (_b = globalConfig.value.button) == null ? void 0 : _b.round;
      }
      return props.round;
    });
    const emit = __emit;
    const customLoadingProps = common_vendor.computed(() => {
      const loadingProps = common_vendor.isDef(props.loadingProps) ? common_vendor.omitBy(props.loadingProps, common_vendor.isUndefined) : {};
      loadingProps.customSpinnerClass = `${common_vendor.isDef(loadingProps.customSpinnerClass) ? loadingProps.customSpinnerClass : ""} wd-button__loading`;
      loadingProps.inheritColor = common_vendor.isDef(loadingProps.inheritColor) ? loadingProps.inheritColor : true;
      return loadingProps;
    });
    const openTypeValue = common_vendor.computed(() => {
      return props.disabled || props.loading ? void 0 : props.openType;
    });
    const isIcon = common_vendor.computed(() => {
      return !slots.default && !props.text && !!(props.icon || props.cssIcon);
    });
    function handleClick(event) {
      if (!props.disabled && !props.loading) {
        emit("click", event);
      }
    }
    function handleGetAuthorize(event) {
      if (props.scope === "phoneNumber") {
        handleGetPhoneNumber(event);
      } else if (props.scope === "userInfo") {
        handleGetUserInfo(event);
      }
    }
    function handleGetUserInfo(event) {
      emit("getuserinfo", event);
    }
    function handleContact(event) {
      emit("contact", event);
    }
    function handleGetPhoneNumber(event) {
      emit("getphonenumber", event);
    }
    function handleGetRealtimePhoneNumber(event) {
      emit("getrealtimephonenumber", event);
    }
    function handleError(event) {
      emit("error", event);
    }
    function handleLaunchApp(event) {
      emit("launchapp", event);
    }
    function handleOpenSetting(event) {
      emit("opensetting", event);
    }
    function handleChooseAvatar(event) {
      emit("chooseavatar", event);
    }
    function handleAgreePrivacyAuthorization(event) {
      emit("agreeprivacyauthorization", event);
    }
    return (_ctx, _cache) => {
      return common_vendor.e({
        a: _ctx.loading
      }, _ctx.loading ? {
        b: common_vendor.p({
          ...customLoadingProps.value
        })
      } : _ctx.icon || _ctx.cssIcon ? {
        d: common_vendor.p({
          ["custom-class"]: "wd-button__icon",
          name: _ctx.icon,
          ["class-prefix"]: _ctx.classPrefix,
          ["css-icon"]: _ctx.cssIcon
        })
      } : {}, {
        c: _ctx.icon || _ctx.cssIcon,
        e: _ctx.$slots.default || _ctx.text
      }, _ctx.$slots.default || _ctx.text ? {
        f: common_vendor.t(_ctx.text)
      } : {}, {
        g: _ctx.buttonId,
        h: `${_ctx.disabled || _ctx.loading ? "" : "wd-button--active"}`,
        i: common_vendor.s(_ctx.customStyle),
        j: common_vendor.n("is-" + effectiveType.value),
        k: common_vendor.n("is-" + effectiveSize.value),
        l: common_vendor.n(isIcon.value ? "is-icon" : ""),
        m: common_vendor.n(effectiveRound.value ? "is-round" : ""),
        n: common_vendor.n(_ctx.hairline ? "is-hairline" : ""),
        o: common_vendor.n(effectiveVariant.value !== "base" ? "is-" + effectiveVariant.value : ""),
        p: common_vendor.n(_ctx.disabled ? "is-disabled" : ""),
        q: common_vendor.n(_ctx.block ? "is-block" : ""),
        r: common_vendor.n(_ctx.loading ? "is-loading" : ""),
        s: common_vendor.n(_ctx.customClass),
        t: _ctx.hoverStartTime,
        v: _ctx.hoverStayTime,
        w: openTypeValue.value,
        x: _ctx.sendMessageTitle,
        y: _ctx.sendMessagePath,
        z: _ctx.sendMessageImg,
        A: _ctx.appParameter,
        B: _ctx.showMessageCard,
        C: _ctx.sessionFrom,
        D: _ctx.lang,
        E: _ctx.hoverStopPropagation,
        F: _ctx.scope,
        G: common_vendor.o(handleClick),
        H: common_vendor.o(handleGetAuthorize),
        I: common_vendor.o(handleGetUserInfo),
        J: common_vendor.o(handleContact),
        K: common_vendor.o(handleGetPhoneNumber),
        L: common_vendor.o(handleGetRealtimePhoneNumber),
        M: common_vendor.o(handleError),
        N: common_vendor.o(handleLaunchApp),
        O: common_vendor.o(handleOpenSetting),
        P: common_vendor.o(handleChooseAvatar),
        Q: common_vendor.o(handleAgreePrivacyAuthorization)
      });
    };
  }
});
exports._sfc_main = _sfc_main;
