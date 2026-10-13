"use strict";
const common_vendor = require("../../../../../common/vendor.js");
const __default__ = {
  name: "wd-transition",
  options: {
    addGlobalClass: true,
    virtualHost: true,
    styleIsolation: "shared"
  }
};
const _sfc_main = /* @__PURE__ */ common_vendor.defineComponent({
  ...__default__,
  props: common_vendor.transitionProps,
  emits: ["click", "before-enter", "enter", "before-leave", "leave", "after-leave", "after-enter"],
  setup(__props, { emit: __emit }) {
    const getClassNames = (name) => {
      const transitionClass = (transitionName, phase) => {
        return `wd-transition--${transitionName}-${phase}`;
      };
      let enter2 = `${props.enterClass} ${props.enterActiveClass}`;
      let enterTo = `${props.enterToClass} ${props.enterActiveClass}`;
      let leave2 = `${props.leaveClass} ${props.leaveActiveClass}`;
      let leaveTo = `${props.leaveToClass} ${props.leaveActiveClass}`;
      if (Array.isArray(name)) {
        for (let index = 0; index < name.length; index++) {
          enter2 = `${transitionClass(name[index], "enter")} ${transitionClass(name[index], "enter-active")} ${enter2}`;
          enterTo = `${transitionClass(name[index], "enter-to")} ${transitionClass(name[index], "enter-active")} ${enterTo}`;
          leave2 = `${transitionClass(name[index], "leave")} ${transitionClass(name[index], "leave-active")} ${leave2}`;
          leaveTo = `${transitionClass(name[index], "leave-to")} ${transitionClass(name[index], "leave-active")} ${leaveTo}`;
        }
      } else if (name) {
        enter2 = `${transitionClass(name, "enter")} ${transitionClass(name, "enter-active")} ${enter2}`;
        enterTo = `${transitionClass(name, "enter-to")} ${transitionClass(name, "enter-active")} ${enterTo}`;
        leave2 = `${transitionClass(name, "leave")} ${transitionClass(name, "leave-active")} ${leave2}`;
        leaveTo = `${transitionClass(name, "leave-to")} ${transitionClass(name, "leave-active")} ${leaveTo}`;
      }
      return {
        enter: enter2,
        "enter-to": enterTo,
        leave: leave2,
        "leave-to": leaveTo
      };
    };
    const props = __props;
    const emit = __emit;
    const inited = common_vendor.ref(false);
    const display = common_vendor.ref(false);
    const status = common_vendor.ref("");
    const transitionEnded = common_vendor.ref(false);
    const currentDuration = common_vendor.ref(300);
    const classes = common_vendor.ref("");
    const enterPromise = common_vendor.ref(null);
    const enterLifeCyclePromises = common_vendor.ref(null);
    const leaveLifeCyclePromises = common_vendor.ref(null);
    const style = common_vendor.computed(() => {
      return `-webkit-transition-duration:${currentDuration.value}ms;transition-duration:${currentDuration.value}ms;${display.value || !props.destroy ? "" : "display: none;"}${props.customStyle}`;
    });
    const rootClass = common_vendor.computed(() => {
      return `wd-transition ${props.customClass}  ${classes.value}`;
    });
    const isShow = common_vendor.computed(() => {
      return !props.lazyRender || inited.value;
    });
    common_vendor.onBeforeMount(() => {
      if (props.show) {
        enter();
      }
    });
    common_vendor.watch(
      () => props.show,
      (newVal) => {
        handleShow(newVal);
      },
      { deep: true }
    );
    function handleClick() {
      emit("click");
    }
    function handleShow(value) {
      if (value) {
        handleAbortPromise();
        enter();
      } else {
        leave();
      }
    }
    function handleAbortPromise() {
      common_vendor.isPromise(enterPromise.value) && enterPromise.value.abort();
      common_vendor.isPromise(enterLifeCyclePromises.value) && enterLifeCyclePromises.value.abort();
      common_vendor.isPromise(leaveLifeCyclePromises.value) && leaveLifeCyclePromises.value.abort();
      enterPromise.value = null;
      enterLifeCyclePromises.value = null;
      leaveLifeCyclePromises.value = null;
    }
    function enter() {
      enterPromise.value = new common_vendor.AbortablePromise(async (resolve) => {
        try {
          const classNames = getClassNames(props.name);
          const duration = common_vendor.isObj(props.duration) ? props.duration.enter : props.duration;
          status.value = "enter";
          emit("before-enter");
          enterLifeCyclePromises.value = common_vendor.pause();
          await enterLifeCyclePromises.value;
          emit("enter");
          classes.value = classNames.enter;
          currentDuration.value = duration;
          enterLifeCyclePromises.value = common_vendor.pause();
          await enterLifeCyclePromises.value;
          inited.value = true;
          display.value = true;
          enterLifeCyclePromises.value = common_vendor.pause();
          await enterLifeCyclePromises.value;
          enterLifeCyclePromises.value = null;
          transitionEnded.value = false;
          classes.value = classNames["enter-to"];
          resolve();
        } catch (error) {
        }
      });
    }
    async function leave() {
      if (!enterPromise.value) {
        transitionEnded.value = false;
        return onTransitionEnd();
      }
      try {
        await enterPromise.value;
        if (!display.value)
          return;
        const classNames = getClassNames(props.name);
        const duration = common_vendor.isObj(props.duration) ? props.duration.leave : props.duration;
        status.value = "leave";
        emit("before-leave");
        currentDuration.value = duration;
        leaveLifeCyclePromises.value = common_vendor.pause();
        await leaveLifeCyclePromises.value;
        emit("leave");
        classes.value = classNames.leave;
        leaveLifeCyclePromises.value = common_vendor.pause();
        await leaveLifeCyclePromises.value;
        transitionEnded.value = false;
        classes.value = classNames["leave-to"];
        leaveLifeCyclePromises.value = setPromise(currentDuration.value);
        await leaveLifeCyclePromises.value;
        leaveLifeCyclePromises.value = null;
        onTransitionEnd();
        enterPromise.value = null;
      } catch (error) {
      }
    }
    function setPromise(duration) {
      return new common_vendor.AbortablePromise((resolve) => {
        const timer = setTimeout(() => {
          clearTimeout(timer);
          resolve();
        }, duration);
      });
    }
    function onTransitionEnd() {
      if (transitionEnded.value)
        return;
      transitionEnded.value = true;
      if (status.value === "leave") {
        emit("after-leave");
      } else if (status.value === "enter") {
        emit("after-enter");
      }
      if (!props.show && display.value) {
        display.value = false;
      }
    }
    function noop() {
    }
    return (_ctx, _cache) => {
      return common_vendor.e({
        a: isShow.value && _ctx.disableTouchMove
      }, isShow.value && _ctx.disableTouchMove ? {
        b: common_vendor.n(rootClass.value),
        c: common_vendor.s(style.value),
        d: common_vendor.o(onTransitionEnd),
        e: common_vendor.o(handleClick),
        f: common_vendor.o(noop)
      } : isShow.value && !_ctx.disableTouchMove ? {
        h: common_vendor.n(rootClass.value),
        i: common_vendor.s(style.value),
        j: common_vendor.o(onTransitionEnd),
        k: common_vendor.o(handleClick)
      } : {}, {
        g: isShow.value && !_ctx.disableTouchMove
      });
    };
  }
});
exports._sfc_main = _sfc_main;
