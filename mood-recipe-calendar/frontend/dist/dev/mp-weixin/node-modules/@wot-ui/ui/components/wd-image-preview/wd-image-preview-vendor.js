"use strict";
const common_vendor = require("../../../../../common/vendor.js");
if (!Math) {
  (wdIcon + wdSwiper + wdOverlay)();
}
const wdIcon = () => "../wd-icon/wd-icon.js";
const wdOverlay = () => "../wd-overlay/wd-overlay.js";
const wdSwiper = () => "../wd-swiper/wd-swiper.js";
const __default__ = {
  name: "wd-image-preview",
  options: {
    addGlobalClass: true,
    virtualHost: true,
    styleIsolation: "shared"
  }
};
const _sfc_main = /* @__PURE__ */ common_vendor.defineComponent({
  ...__default__,
  props: common_vendor.imagePreviewProps,
  emits: ["open", "close", "click", "change", "long-press"],
  setup(__props, { expose: __expose, emit: __emit }) {
    const props = __props;
    const emit = __emit;
    const state = common_vendor.reactive({
      show: false,
      visible: false,
      currentIndex: 0,
      images: []
    });
    const imagePreviewOptionKey = common_vendor.getImagePreviewOptionKey(props.selector);
    const imagePreviewOption = common_vendor.inject(imagePreviewOptionKey, common_vendor.ref(common_vendor.defaultOptions));
    const options = common_vendor.computed(() => ({
      showIndex: common_vendor.isDef(imagePreviewOption.value.showIndex) ? imagePreviewOption.value.showIndex : props.showIndex,
      loop: common_vendor.isDef(imagePreviewOption.value.loop) ? imagePreviewOption.value.loop : props.loop,
      closeable: common_vendor.isDef(imagePreviewOption.value.closeable) ? imagePreviewOption.value.closeable : props.closeable,
      closeIcon: common_vendor.isDef(imagePreviewOption.value.closeIcon) ? imagePreviewOption.value.closeIcon : props.closeIcon,
      closeIconPosition: common_vendor.isDef(imagePreviewOption.value.closeIconPosition) ? imagePreviewOption.value.closeIconPosition : props.closeIconPosition,
      closeOnClick: common_vendor.isDef(imagePreviewOption.value.closeOnClick) ? imagePreviewOption.value.closeOnClick : props.closeOnClick,
      showMenuByLongpress: common_vendor.isDef(imagePreviewOption.value.showMenuByLongpress) ? imagePreviewOption.value.showMenuByLongpress : props.showMenuByLongpress,
      zIndex: common_vendor.isDef(imagePreviewOption.value.zIndex) ? imagePreviewOption.value.zIndex : props.zIndex,
      onOpen: imagePreviewOption.value.onOpen || props.onOpen || null,
      onClose: imagePreviewOption.value.onClose || props.onClose || null,
      onChange: imagePreviewOption.value.onChange || props.onChange || null,
      onLongPress: imagePreviewOption.value.onLongPress || props.onLongPress || null
    }));
    common_vendor.watch(
      () => imagePreviewOption.value,
      (newVal) => {
        reset(newVal);
      },
      { deep: true, immediate: true }
    );
    common_vendor.watch(
      () => state.show,
      (newVal, oldVal) => {
        if (newVal && !oldVal) {
          emit("open");
          if (common_vendor.isFunction(options.value.onOpen)) {
            options.value.onOpen();
          }
        } else if (!newVal && oldVal) {
          emit("close");
          if (common_vendor.isFunction(options.value.onClose)) {
            options.value.onClose();
          }
        }
      }
    );
    function reset(option) {
      state.show = common_vendor.isDef(option.show) ? option.show : false;
      if (state.show) {
        state.images = common_vendor.isDef(option.images) ? option.images : props.images || [];
        state.currentIndex = common_vendor.isDef(option.startPosition) ? option.startPosition : props.startPosition;
      }
    }
    function handleEnter() {
      state.visible = true;
    }
    function handleAfterLeave() {
      state.visible = false;
      state.images = [];
      state.currentIndex = 0;
    }
    function open(opts) {
      if (opts) {
        if (Array.isArray(opts)) {
          state.images = opts;
        } else {
          state.images = common_vendor.isDef(opts.images) ? opts.images : props.images || [];
          state.currentIndex = common_vendor.isDef(opts.startPosition) ? opts.startPosition : props.startPosition;
        }
      }
      state.show = true;
    }
    function close() {
      state.show = false;
    }
    function setActive(index) {
      if (index >= 0 && index < state.images.length) {
        state.currentIndex = index;
      }
    }
    function handleSwiperChange({ current }) {
      state.currentIndex = current;
      emit("change", { index: current });
      if (common_vendor.isFunction(options.value.onChange)) {
        options.value.onChange(current);
      }
    }
    function handleImageClick({ index }) {
      emit("click", { index });
      if (options.value.closeOnClick) {
        close();
      }
    }
    __expose({
      open,
      close,
      setActive
    });
    return (_ctx, _cache) => {
      return common_vendor.e({
        a: options.value.closeable
      }, options.value.closeable ? {
        b: common_vendor.p({
          ["custom-class"]: "wd-image-preview__close-icon",
          name: options.value.closeIcon
        }),
        c: common_vendor.n(`wd-image-preview__close wd-image-preview__close--${options.value.closeIconPosition}`),
        d: common_vendor.o(close)
      } : {}, {
        e: common_vendor.r("close", {
          close
        }),
        f: state.visible
      }, state.visible ? {
        g: common_vendor.w(({
          total,
          current
        }, s0, i0) => {
          return common_vendor.e({
            a: options.value.showIndex && total > 1
          }, options.value.showIndex && total > 1 ? {
            b: common_vendor.t(current + 1),
            c: common_vendor.t(total)
          } : {}, {
            d: common_vendor.r("indicator", {
              total,
              current
            }),
            e: i0,
            f: s0
          });
        }, {
          name: "indicator",
          path: "g",
          vueId: "5e976dea-2,5e976dea-0"
        }),
        h: common_vendor.o(handleImageClick),
        i: common_vendor.o(handleSwiperChange),
        j: common_vendor.o(($event) => state.currentIndex = $event),
        k: common_vendor.p({
          ["custom-class"]: "wd-image-preview__swiper",
          list: state.images,
          autoplay: false,
          loop: options.value.loop,
          ["show-menu-by-longpress"]: options.value.showMenuByLongpress,
          ["image-mode"]: "aspectFit",
          height: "100%",
          current: state.currentIndex
        })
      } : {}, {
        l: common_vendor.r("d", {
          current: state.currentIndex,
          total: state.images.length,
          images: state.images,
          close
        }),
        m: common_vendor.o(handleEnter),
        n: common_vendor.o(handleAfterLeave),
        o: common_vendor.p({
          show: state.show,
          ["z-index"]: options.value.zIndex,
          ["lock-scroll"]: true,
          ["custom-class"]: `wd-image-preview ${_ctx.customClass}`,
          ["custom-style"]: _ctx.customStyle
        })
      });
    };
  }
});
exports._sfc_main = _sfc_main;
