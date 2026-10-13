"use strict";
const common_vendor = require("../../common/vendor.js");
const api_fridge = require("../../api/fridge.js");
const composables_useNavBar = require("../../composables/useNavBar.js");
const utils_assets = require("../../utils/assets.js");
const api_request = require("../../api/request.js");
const utils_chooseImage = require("../../utils/chooseImage.js");
const utils_login = require("../../utils/login.js");
const utils_memberGate = require("../../utils/memberGate.js");
const utils_toast = require("../../utils/toast.js");
const __unplugin_components_1 = () => "../../node-modules/@wot-ui/ui/components/wd-tour/wd-tour.js";
const __unplugin_components_0 = () => "../../node-modules/@wot-ui/ui/components/wd-navbar/wd-navbar.js";
if (!Array) {
  const _component_wd_navbar = __unplugin_components_0;
  const _component_wd_tour = __unplugin_components_1;
  const _component_layout_default_uni = common_vendor.resolveComponent("layout-default-uni");
  (_component_wd_navbar + _component_wd_tour + _component_layout_default_uni)();
}
if (!Math) {
  GuozaiButton();
}
const GuozaiButton = () => "../../components/guozai/GuozaiButton.js";
const FRIDGE_TOUR_KEY = "mrc-tour-fridge-version";
const FRIDGE_TOUR_VERSION = 1;
const _sfc_defineComponent = common_vendor.defineComponent({
  __name: "index",
  setup(__props) {
    const GZ_INSPECT = `${utils_assets.STATIC_BASE_URL}/static/guozai/action_07_empty.png`;
    const GZ_CAMERA = `${utils_assets.STATIC_BASE_URL}/static/guozai/action_03_camera.png`;
    const GZ_BELL = `${utils_assets.STATIC_BASE_URL}/static/guozai/action_14_clap.png`;
    common_vendor.onShareAppMessage(() => {
      var _a;
      const pages = getCurrentPages();
      const route = ((_a = pages[pages.length - 1]) == null ? void 0 : _a.route) || "";
      return { title: "锅仔 · 按心情帮你决定今天吃什么", path: `/${route}` };
    });
    common_vendor.onShareTimeline(() => ({ title: "锅仔 · 按心情帮你决定今天吃什么" }));
    const now = /* @__PURE__ */ new Date();
    const today = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, "0")}-${String(now.getDate()).padStart(2, "0")}`;
    const items = common_vendor.ref([]);
    const summary = common_vendor.ref({ total: 0, soon: 0, expired: 0, priorityItems: [] });
    const loading = common_vendor.ref(true);
    const saving = common_vendor.ref(false);
    const formOpen = common_vendor.ref(false);
    const editingId = common_vendor.ref();
    const error = common_vendor.ref("");
    const formError = common_vendor.ref("");
    const form = common_vendor.ref({ name: "", quantity: "1", unit: "份", purchasedOn: today, expiresOn: "", note: "" });
    const formAdvice = common_vendor.ref(null);
    let adviceTimer;
    common_vendor.watch(() => form.value.name, (name) => {
      if (adviceTimer)
        clearTimeout(adviceTimer);
      const trimmed = name == null ? void 0 : name.trim();
      if (!trimmed) {
        formAdvice.value = null;
        return;
      }
      adviceTimer = setTimeout(async () => {
        try {
          const advice = await api_fridge.fetchStorageAdvice(trimmed);
          formAdvice.value = advice.inFridgeWarned && advice.level ? { level: advice.level, tip: advice.tip || "" } : null;
        } catch {
          formAdvice.value = null;
        }
      }, 350);
    });
    const soonItems = common_vendor.computed(() => items.value.filter((item) => item.status === "SOON"));
    const expiredItems = common_vendor.computed(() => items.value.filter((item) => item.status === "EXPIRED"));
    const otherItems = common_vendor.computed(() => items.value.filter((item) => item.status === "FRESH" || item.status === "NO_DATE"));
    const dueCount = common_vendor.computed(() => soonItems.value.length + expiredItems.value.length);
    const shownDue = common_vendor.ref(0);
    let rollTimer;
    function rollDue(to) {
      if (rollTimer) {
        clearInterval(rollTimer);
        rollTimer = void 0;
      }
      if (!to) {
        shownDue.value = to;
        return;
      }
      shownDue.value = 0;
      const step = Math.max(1, Math.ceil(to / 8));
      rollTimer = setInterval(() => {
        const next = shownDue.value + step;
        if (next >= to) {
          shownDue.value = to;
          clearInterval(rollTimer);
          rollTimer = void 0;
        } else {
          shownDue.value = next;
        }
      }, 70);
    }
    common_vendor.watch(dueCount, (to) => {
      rollDue(to);
    });
    common_vendor.onUnmounted(() => {
      if (rollTimer)
        clearInterval(rollTimer);
    });
    const recognizing = common_vendor.ref(false);
    const recognizeOpen = common_vendor.ref(false);
    const recognizeNotice = common_vendor.ref("");
    const drafts = common_vendor.ref([]);
    const recognizedCount = common_vendor.computed(() => drafts.value.filter((d) => d.checked).length);
    function startRecognize() {
      if (!utils_memberGate.requireMember("fridge_recognize"))
        return;
      utils_chooseImage.chooseImageFile({ onSelected: (path) => void runRecognize(path) });
    }
    async function runRecognize(localPath) {
      var _a;
      recognizing.value = true;
      try {
        await utils_login.ensureLogin();
        const uploaded = await api_request.uploadFile(localPath);
        if (!(uploaded == null ? void 0 : uploaded.url))
          throw new Error("照片上传失败，请重试");
        const result = await api_fridge.recognizeFridgeImage(uploaded.url);
        if (result.degraded || !((_a = result.items) == null ? void 0 : _a.length)) {
          utils_toast.toastError(new Error(result.notice || "没认出食材"), "识别失败");
          return;
        }
        drafts.value = result.items.map((item) => ({ ...item, checked: true }));
        recognizeNotice.value = result.notice || "";
        recognizeOpen.value = true;
      } catch (e) {
        utils_toast.toastError(e, "识别失败，请稍后再试");
      } finally {
        recognizing.value = false;
      }
    }
    function closeRecognize() {
      if (saving.value)
        return;
      recognizeOpen.value = false;
      drafts.value = [];
    }
    async function confirmRecognize() {
      const picked = drafts.value.filter((d) => d.checked && d.name.trim());
      if (!picked.length) {
        utils_toast.toast("请至少勾选一项食材");
        return;
      }
      const risky = picked.filter((d) => d.storageLevel === "WORSE");
      if (risky.length) {
        const confirmed = await new Promise((resolve) => {
          common_vendor.index.showModal({
            title: "这些食材其实不该放冰箱",
            content: `${risky.map((d) => d.name).join("、")} 放冰箱会坏得更快，确定仍要放入吗？`,
            confirmText: "仍要放入",
            cancelText: "再看看",
            success: (res) => resolve(res.confirm),
            fail: () => resolve(false)
          });
        });
        if (!confirmed)
          return;
      }
      saving.value = true;
      try {
        for (const draft of picked) {
          await api_fridge.createFridgeItem({
            name: draft.name.trim(),
            quantity: 1,
            unit: "份",
            purchasedOn: today,
            expiresOn: draft.expiresOn || void 0,
            note: draft.shelfLifeMatched ? "" : "保质期为估算，请按实际情况调整"
          });
        }
        closeRecognize();
        await load();
        common_vendor.index.showToast({ title: `已放入 ${picked.length} 项食材`, icon: "success" });
      } catch (e) {
        utils_toast.toastError(e, "保存失败，请稍后再试");
      } finally {
        saving.value = false;
      }
    }
    async function sendExpiryNotice() {
      if (!utils_memberGate.requireMember("fridge_expiry"))
        return;
      try {
        const result = await api_fridge.notifyFridgeExpiring();
        if (result.sent)
          utils_toast.toast(`已提醒你 ${result.count} 项临期食材：${result.names}`);
        else
          utils_toast.toast("暂时没有临期食材，冰箱状态不错");
      } catch (e) {
        utils_toast.toastError(e, "提醒发送失败");
      }
    }
    const showFridgeTour = common_vendor.ref(false);
    const fridgeTourCurrent = common_vendor.ref(0);
    const fridgeTourSteps = [
      { element: "#tour-fridge-stock", content: "食材按「已过期 / 优先消耗 / 其他」分区摆，快坏的永远排在最前面。", placement: "bottom" },
      { element: "#tour-fridge-vision", content: "对着冰箱拍一张，锅仔自动认食材、算保质期，不用一个个手输。", placement: "bottom" },
      { element: "#tour-fridge-expiry", content: "有食材快过期时，这里会出现提醒，点「提醒我」让锅仔盯着。", placement: "top" }
    ];
    function startFridgeTour() {
      if (loading.value || showFridgeTour.value || Number(common_vendor.index.getStorageSync(FRIDGE_TOUR_KEY)) >= FRIDGE_TOUR_VERSION)
        return;
      fridgeTourCurrent.value = 0;
      common_vendor.nextTick$1(() => {
        showFridgeTour.value = true;
      });
    }
    function finishFridgeTour() {
      common_vendor.index.setStorageSync(FRIDGE_TOUR_KEY, FRIDGE_TOUR_VERSION);
      showFridgeTour.value = false;
    }
    async function load() {
      loading.value = true;
      error.value = "";
      try {
        await utils_login.ensureLogin();
        const [list, overview] = await Promise.all([api_fridge.fetchFridgeItems(), api_fridge.fetchFridgeSummary()]);
        items.value = list;
        summary.value = overview;
        rollDue(soonItems.value.length + expiredItems.value.length);
      } catch (e) {
        error.value = "冰箱暂时打不开，请稍后再试";
        utils_toast.toastError(e, error.value);
      } finally {
        loading.value = false;
      }
    }
    common_vendor.onShow(() => {
      load();
      setTimeout(startFridgeTour, 600);
    });
    function openCreate() {
      editingId.value = void 0;
      formError.value = "";
      form.value = { name: "", quantity: "1", unit: "份", purchasedOn: today, expiresOn: "", note: "" };
      formOpen.value = true;
    }
    function openEdit(item) {
      editingId.value = item.id;
      formError.value = "";
      form.value = { name: item.name, quantity: String(item.quantity), unit: item.unit, purchasedOn: item.purchasedOn || today, expiresOn: item.expiresOn || "", note: item.note || "" };
      formOpen.value = true;
    }
    function closeForm() {
      if (!saving.value)
        formOpen.value = false;
    }
    function setDate(field, event) {
      form.value[field] = event.detail.value;
    }
    function validateForm() {
      const quantity = Number(form.value.quantity);
      if (!form.value.name.trim())
        return "请填写食材名称";
      if (!Number.isFinite(quantity) || quantity <= 0)
        return "数量要大于 0";
      if (!form.value.unit.trim())
        return "请填写单位";
      if (form.value.expiresOn && form.value.purchasedOn && form.value.expiresOn < form.value.purchasedOn)
        return "保质期不能早于购买日期";
      return "";
    }
    async function save() {
      formError.value = validateForm();
      if (formError.value)
        return;
      saving.value = true;
      try {
        const payload = { name: form.value.name.trim(), quantity: Number(form.value.quantity), unit: form.value.unit.trim(), purchasedOn: form.value.purchasedOn, expiresOn: form.value.expiresOn || void 0, note: form.value.note.trim() || void 0 };
        if (editingId.value)
          await api_fridge.updateFridgeItem(editingId.value, payload);
        else
          await api_fridge.createFridgeItem(payload);
        formOpen.value = false;
        await load();
        common_vendor.index.showToast({ title: editingId.value ? "已更新" : "已放入冰箱", icon: "success" });
      } catch (e) {
        utils_toast.toastError(e, "保存失败，请稍后再试");
      } finally {
        saving.value = false;
      }
    }
    async function consume(item) {
      try {
        await api_fridge.consumeFridgeItem(item.id);
        await load();
        common_vendor.index.showToast({ title: "已记为用掉 1 份", icon: "success" });
      } catch (e) {
        utils_toast.toastError(e, "更新库存失败");
      }
    }
    function remove(item) {
      common_vendor.index.showModal({ title: "移出冰箱？", content: `确认删除「${item.name}」吗？`, success: async (result) => {
        if (!result.confirm)
          return;
        try {
          await api_fridge.deleteFridgeItem(item.id);
          await load();
        } catch (e) {
          utils_toast.toastError(e, "删除失败");
        }
      } });
    }
    function statusText(item) {
      if (item.status === "EXPIRED")
        return "已过期";
      if (item.status === "SOON")
        return item.daysLeft === 0 ? "今天到期" : `${item.daysLeft}天内到期`;
      if (item.status === "NO_DATE")
        return "未设置日期";
      return "新鲜";
    }
    return (_ctx, _cache) => {
      return common_vendor.e({
        a: common_vendor.o(common_vendor.unref(composables_useNavBar.navBack)),
        b: common_vendor.p({
          title: "我的冰箱",
          ["left-arrow"]: true,
          ["safe-area-inset-top"]: true,
          ["custom-style"]: "background-color: transparent !important;"
        }),
        c: loading.value
      }, loading.value ? {} : error.value ? {
        e: common_vendor.o(load)
      } : common_vendor.e({
        f: GZ_INSPECT,
        g: common_vendor.t(dueCount.value ? `有 ${dueCount.value} 项再不吃就坏了` : "都还新鲜，慢慢吃"),
        h: common_vendor.t(shownDue.value),
        i: !dueCount.value ? 1 : "",
        j: common_vendor.t(summary.value.total),
        k: common_vendor.o(openCreate),
        l: common_vendor.p({
          variant: "secondary",
          block: false,
          ["aria-label"]: "添加食材"
        }),
        m: GZ_CAMERA,
        n: common_vendor.o(startRecognize),
        o: recognizing.value
      }, recognizing.value ? {} : {}, {
        p: !items.value.length
      }, !items.value.length ? {
        q: common_vendor.o(openCreate),
        r: common_vendor.p({
          ["aria-label"]: "添加第一项食材"
        })
      } : common_vendor.e({
        s: expiredItems.value.length
      }, expiredItems.value.length ? {
        t: common_vendor.t(expiredItems.value.length),
        v: common_vendor.f(expiredItems.value, (item, k0, i0) => {
          return {
            a: common_vendor.t(item.name),
            b: common_vendor.t(item.quantity),
            c: common_vendor.t(item.unit),
            d: common_vendor.t(statusText(item)),
            e: common_vendor.o(($event) => openEdit(item), item.id),
            f: common_vendor.o(($event) => remove(item), item.id),
            g: item.id
          };
        })
      } : {}, {
        w: soonItems.value.length
      }, soonItems.value.length ? {
        x: common_vendor.t(soonItems.value.length),
        y: common_vendor.f(soonItems.value, (item, k0, i0) => {
          return {
            a: common_vendor.t(item.name),
            b: common_vendor.t(item.quantity),
            c: common_vendor.t(item.unit),
            d: common_vendor.t(statusText(item)),
            e: common_vendor.t(item.unit),
            f: common_vendor.o(($event) => consume(item), item.id),
            g: common_vendor.o(($event) => openEdit(item), item.id),
            h: item.id
          };
        })
      } : {}, {
        z: otherItems.value.length
      }, otherItems.value.length ? {
        A: common_vendor.t(otherItems.value.length),
        B: common_vendor.f(otherItems.value, (item, k0, i0) => {
          return {
            a: common_vendor.t(item.name),
            b: common_vendor.t(item.quantity),
            c: common_vendor.t(item.unit),
            d: common_vendor.t(statusText(item)),
            e: common_vendor.t(item.unit),
            f: common_vendor.o(($event) => consume(item), item.id),
            g: common_vendor.o(($event) => openEdit(item), item.id),
            h: item.id
          };
        })
      } : {}, {
        C: soonItems.value.length
      }, soonItems.value.length ? {
        D: common_vendor.t(soonItems.value.map((i) => i.name).join("、")),
        E: GZ_BELL,
        F: common_vendor.o(sendExpiryNotice)
      } : {})), {
        d: error.value,
        G: recognizeOpen.value
      }, recognizeOpen.value ? common_vendor.e({
        H: common_vendor.o(closeRecognize),
        I: common_vendor.t(recognizeNotice.value || "已自动识别并估算保质期，勾选后放入冰箱"),
        J: !drafts.value.length
      }, !drafts.value.length ? {} : {}, {
        K: common_vendor.f(drafts.value, (draft, idx, i0) => {
          return common_vendor.e({
            a: common_vendor.t(draft.checked ? "✓" : ""),
            b: draft.checked ? 1 : "",
            c: draft.checked,
            d: common_vendor.o(($event) => draft.checked = !draft.checked, idx),
            e: draft.name,
            f: common_vendor.o(($event) => draft.name = $event.detail.value, idx),
            g: common_vendor.t(draft.shelfLifeDays),
            h: common_vendor.t(draft.expiresOn),
            i: common_vendor.t(draft.shelfLifeMatched ? "" : "（估算）"),
            j: draft.storageLevel
          }, draft.storageLevel ? {
            k: common_vendor.t(draft.storageLevel === "WORSE" ? "⚠ 放冰箱会坏得更快" : "· 其实不用放冰箱"),
            l: common_vendor.t(draft.storageTip),
            m: common_vendor.n(`is-${draft.storageLevel.toLowerCase()}`)
          } : {}, {
            n: idx
          });
        }),
        L: common_vendor.t(recognizedCount.value),
        M: common_vendor.o(confirmRecognize),
        N: common_vendor.p({
          loading: saving.value,
          ["aria-label"]: "确认放入冰箱"
        }),
        O: common_vendor.o(() => {
        }),
        P: common_vendor.o(closeRecognize)
      }) : {}, {
        Q: formOpen.value
      }, formOpen.value ? common_vendor.e({
        R: common_vendor.t(editingId.value ? "编辑食材" : "放入冰箱"),
        S: common_vendor.o(closeForm),
        T: form.value.name,
        U: common_vendor.o(($event) => form.value.name = $event.detail.value),
        V: formAdvice.value
      }, formAdvice.value ? {
        W: common_vendor.t(formAdvice.value.level === "WORSE" ? "⚠ 这种食材放冰箱会坏得更快" : "· 这种食材其实不用放冰箱"),
        X: common_vendor.t(formAdvice.value.tip),
        Y: common_vendor.n(`is-${formAdvice.value.level.toLowerCase()}`)
      } : {}, {
        Z: form.value.quantity,
        aa: common_vendor.o(($event) => form.value.quantity = $event.detail.value),
        ab: form.value.unit,
        ac: common_vendor.o(($event) => form.value.unit = $event.detail.value),
        ad: common_vendor.t(form.value.purchasedOn || "选择日期"),
        ae: form.value.purchasedOn,
        af: common_vendor.o(($event) => setDate("purchasedOn", $event)),
        ag: common_vendor.t(form.value.expiresOn || "未设置"),
        ah: form.value.expiresOn || today,
        ai: common_vendor.o(($event) => setDate("expiresOn", $event)),
        aj: form.value.note,
        ak: common_vendor.o(($event) => form.value.note = $event.detail.value),
        al: formError.value
      }, formError.value ? {
        am: common_vendor.t(formError.value)
      } : {}, {
        an: common_vendor.o(save),
        ao: common_vendor.p({
          loading: saving.value,
          ["aria-label"]: "保存食材"
        }),
        ap: common_vendor.o(() => {
        }),
        aq: common_vendor.o(closeForm)
      }) : {}, {
        ar: common_vendor.o(finishFridgeTour),
        as: common_vendor.o(finishFridgeTour),
        at: common_vendor.o(($event) => showFridgeTour.value = $event),
        av: common_vendor.o(($event) => fridgeTourCurrent.value = $event),
        aw: common_vendor.p({
          steps: fridgeTourSteps,
          padding: 10,
          ["border-radius"]: 18,
          ["z-index"]: 1200,
          ["custom-nav"]: true,
          ["missing-strategy"]: "skip",
          ["skip-text"]: "不再提示",
          ["finish-text"]: "知道啦",
          ["custom-style"]: "--wot-tour-popover-bg: var(--mrc-surface); --wot-tour-info-color: var(--mrc-text); --wot-tour-popover-radius: 16px; --wot-tour-popover-padding: 18px;",
          modelValue: showFridgeTour.value,
          current: fridgeTourCurrent.value
        })
      });
    };
  }
});
_sfc_defineComponent.__runtimeHooks = 6;
const MiniProgramPage = /* @__PURE__ */ common_vendor._export_sfc(_sfc_defineComponent, [["__scopeId", "data-v-f26f7345"]]);
exports.MiniProgramPage = MiniProgramPage;
