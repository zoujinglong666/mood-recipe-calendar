"use strict";
const common_vendor = require("../../common/vendor.js");
const utils_assets = require("../../utils/assets.js");
const api_records = require("../../api/records.js");
const api_request = require("../../api/request.js");
const utils_chooseImage = require("../../utils/chooseImage.js");
const utils_cookingDraft = require("../../utils/cookingDraft.js");
const utils_login = require("../../utils/login.js");
const utils_toast = require("../../utils/toast.js");
const utils_bus = require("../../utils/bus.js");
const stores_user = require("../../stores/user.js");
const api_share = require("../../api/share.js");
const __unplugin_components_0 = () => "../../node-modules/@wot-ui/ui/components/wd-navbar/wd-navbar.js";
if (!Array) {
  const _component_wd_navbar = __unplugin_components_0;
  const _component_layout_tabbar_uni = common_vendor.resolveComponent("layout-tabbar-uni");
  (_component_wd_navbar + _component_layout_tabbar_uni)();
}
if (!Math) {
  (Icon + MoodPicker + GuozaiChoiceChips + GuozaiInsightCard + SuccessModal)();
}
const Icon = () => "../../components/common/Icon.js";
const GuozaiChoiceChips = () => "../../components/guozai/GuozaiChoiceChips.js";
const GuozaiInsightCard = () => "../../components/guozai/GuozaiInsightCard.js";
const MoodPicker = () => "../../components/guozai/MoodPicker.js";
const SuccessModal = () => "../../components/guozai/SuccessModal.js";
const RECORD_DATE_KEY = "mrc_record_date";
const UPLOAD_CONCURRENCY = 2;
const _sfc_defineComponent = common_vendor.defineComponent({
  __name: "index",
  setup(__props) {
    const router = common_vendor.useRouter();
    const userStore = stores_user.useUserStore();
    common_vendor.onShareAppMessage(() => {
      const sharer = userStore.openid;
      const path = `/pages/record/index${sharer ? `?sharer=${encodeURIComponent(sharer)}` : ""}`;
      void api_share.recordShare("record").catch(() => {
      });
      return { title: "锅仔 · 记录今天吃了什么", path };
    });
    common_vendor.onShareTimeline(() => {
      const sharer = userStore.openid;
      return {
        title: "锅仔 · 记录今天吃了什么",
        query: sharer ? `sharer=${encodeURIComponent(sharer)}` : ""
      };
    });
    const dishName = common_vendor.ref("");
    const selectedMood = common_vendor.ref("");
    const note = common_vendor.ref("");
    const cookingTime = common_vendor.ref("30分钟");
    const photos = common_vendor.ref([]);
    const recipeId = common_vendor.ref();
    const exposureId = common_vendor.ref();
    const clientRequestId = common_vendor.ref(utils_cookingDraft.createRequestId());
    const savedRecordId = common_vendor.ref();
    const showSuccess = common_vendor.ref(false);
    const submitting = common_vendor.ref(false);
    const editingId = common_vendor.ref();
    const recordDate = common_vendor.ref();
    const feedbackSelections = common_vendor.ref([]);
    const learningReceipt = common_vendor.ref();
    const isEditing = common_vendor.computed(() => Boolean(editingId.value));
    const uploading = common_vendor.computed(() => photos.value.some((photo) => photo.uploading));
    const feedbackOptions = [
      { value: "liked", label: "喜欢这道菜" },
      { value: "tooHard", label: "做起来太难" },
      { value: "leftover", label: "有剩菜" }
    ];
    common_vendor.onShow(async () => {
      const editId = Number(common_vendor.index.getStorageSync("mrc_record_edit_id"));
      if (editId && editId !== editingId.value) {
        common_vendor.index.removeStorageSync("mrc_record_edit_id");
        await loadForEdit(editId);
        return;
      }
      if (isEditing.value)
        return;
      try {
        const d = common_vendor.index.getStorageSync(utils_cookingDraft.RECORD_DRAFT_KEY);
        if (d) {
          if (d.dish)
            dishName.value = d.dish;
          if (d.mood)
            selectedMood.value = d.mood;
          if (d.recipeId !== void 0 && d.recipeId !== null)
            recipeId.value = Number(d.recipeId) || void 0;
          if (d.exposureId)
            exposureId.value = String(d.exposureId);
          if (d.cookingTime)
            cookingTime.value = `${Number(d.cookingTime)}分钟`;
          if (d.clientRequestId)
            clientRequestId.value = String(d.clientRequestId);
        }
        const backfillDate = common_vendor.index.getStorageSync(RECORD_DATE_KEY);
        if (backfillDate)
          recordDate.value = String(backfillDate);
      } catch {
      }
    });
    function isBackfill() {
      if (!recordDate.value)
        return false;
      return recordDate.value < (/* @__PURE__ */ new Date()).toISOString().slice(0, 10);
    }
    async function addImages() {
      if (uploading.value) {
        utils_toast.toast("图片上传中，请稍候");
        return;
      }
      const capacity = 9 - photos.value.length;
      if (!capacity) {
        utils_toast.toast("一条记录最多添加 9 张照片");
        return;
      }
      utils_chooseImage.chooseImageFiles({
        count: capacity,
        onSelected: (tempPaths) => uploadImages(tempPaths),
        onFail: () => utils_toast.toast("选择图片失败，请重试")
      });
    }
    async function uploadImages(tempPaths) {
      const startIndex = photos.value.length;
      photos.value.push(...tempPaths.slice(0, 9 - photos.value.length).map((localUrl) => ({ localUrl, remoteUrl: "", uploading: true })));
      const queue = photos.value.slice(startIndex);
      console.log(`[upload] 照片队列开始: ${queue.length} 张（并发 ${UPLOAD_CONCURRENCY}），已有 ${startIndex} 张`);
      const failed = /* @__PURE__ */ new Set();
      let cursor = 0;
      async function worker() {
        while (cursor < queue.length) {
          const photo = queue[cursor++];
          try {
            photo.remoteUrl = (await api_request.uploadFile(photo.localUrl)).url;
            console.log("[upload] 单张完成:", photo.localUrl);
          } catch (e) {
            console.warn("[upload] 单张失败:", photo.localUrl, e == null ? void 0 : e.message);
            failed.add(photo.localUrl);
          } finally {
            photo.uploading = false;
          }
        }
      }
      await Promise.all(Array.from({ length: Math.min(UPLOAD_CONCURRENCY, queue.length) }, () => worker()));
      if (failed.size) {
        photos.value = photos.value.filter((item) => !failed.has(item.localUrl));
        utils_toast.toastError(new Error(`${failed.size} 张照片上传失败，请重新添加`), "部分照片上传失败");
      }
      console.log(`[upload] 照片队列结束: 成功 ${queue.filter((photo) => photo.remoteUrl).length}/${queue.length} 张`);
      if (queue.some((photo) => photo.remoteUrl) && !failed.size)
        utils_toast.toastSuccess("照片已收好");
    }
    function removePhoto(index) {
      photos.value.splice(index, 1);
    }
    function movePhoto(index, direction) {
      const target = index + direction;
      if (target < 0 || target >= photos.value.length)
        return;
      const [photo] = photos.value.splice(index, 1);
      photos.value.splice(target, 0, photo);
    }
    async function loadForEdit(id) {
      try {
        const record = await api_records.fetchRecord(id);
        editingId.value = record.id;
        recordDate.value = record.recordDate;
        dishName.value = record.dishName;
        selectedMood.value = record.moodTag;
        note.value = record.note || "";
        cookingTime.value = `${record.cookingTime || 30}分钟`;
        recipeId.value = record.recipeId;
        exposureId.value = record.exposureId;
        photos.value = (record.imageUrls || [record.imageUrl]).map((url) => ({ localUrl: url, remoteUrl: url, uploading: false }));
      } catch (e) {
        utils_toast.toastError(e, "记录加载失败");
        router.back();
      }
    }
    async function publish() {
      var _a, _b, _c;
      if (submitting.value)
        return;
      if (uploading.value) {
        utils_toast.toast("图片上传中，请稍候");
        return;
      }
      if (!dishName.value.trim()) {
        utils_toast.toast("请输入菜名");
        return;
      }
      if (!selectedMood.value) {
        utils_toast.toast("请选择今天的心情");
        return;
      }
      submitting.value = true;
      try {
        const openid = await utils_login.ensureLogin();
        const payload = {
          openid,
          imageUrl: ((_a = photos.value[0]) == null ? void 0 : _a.remoteUrl) || ((_b = photos.value[0]) == null ? void 0 : _b.localUrl) || "",
          imageUrls: photos.value.map((photo) => photo.remoteUrl || photo.localUrl),
          dishName: dishName.value.trim(),
          moodTag: selectedMood.value,
          note: note.value,
          recipeId: recipeId.value,
          exposureId: exposureId.value,
          clientRequestId: clientRequestId.value,
          cookingTime: Number.parseInt(cookingTime.value) || 30,
          recordDate: recordDate.value,
          liked: feedbackSelections.value.includes("liked"),
          tooHard: feedbackSelections.value.includes("tooHard"),
          leftover: feedbackSelections.value.includes("leftover")
        };
        let saved;
        if (isEditing.value) {
          saved = await api_records.updateRecord(editingId.value, payload);
          learningReceipt.value = void 0;
        } else {
          const result = await api_records.saveRecord(payload);
          saved = result.record;
          learningReceipt.value = result.learningReceipt;
          if ((_c = result.newlyUnlocked) == null ? void 0 : _c.length) {
            utils_toast.toastSuccess(`🎉 解锁成就：${result.newlyUnlocked.join("、")}`);
            const unread = Number(common_vendor.index.getStorageSync("mrc_achv_unread") || 0);
            common_vendor.index.setStorageSync("mrc_achv_unread", unread + result.newlyUnlocked.length);
          }
        }
        savedRecordId.value = saved.id;
        common_vendor.index.removeStorageSync(utils_cookingDraft.RECORD_DRAFT_KEY);
        common_vendor.index.removeStorageSync("mrc_companion_message");
        utils_bus.bus.emit(utils_bus.MRC_EVENTS.RECORDS_CHANGED);
        common_vendor.index.removeStorageSync(RECORD_DATE_KEY);
        showSuccess.value = true;
      } catch (e) {
        console.error(e);
        utils_toast.toastError(e, "保存失败");
      } finally {
        submitting.value = false;
      }
    }
    function onSuccessConfirm() {
      showSuccess.value = false;
      resetForm();
      router.push({ name: "timeline" });
    }
    function onSuccessSecondary() {
      showSuccess.value = false;
      resetForm();
      router.pushTab({ name: "home" });
    }
    function resetForm() {
      dishName.value = "";
      selectedMood.value = "";
      note.value = "";
      cookingTime.value = "30分钟";
      photos.value = [];
      recipeId.value = void 0;
      exposureId.value = void 0;
      clientRequestId.value = utils_cookingDraft.createRequestId();
      editingId.value = void 0;
      recordDate.value = void 0;
      savedRecordId.value = void 0;
      feedbackSelections.value = [];
      learningReceipt.value = void 0;
    }
    const COOKING_TIME_OPTIONS = ["10分钟", "20分钟", "30分钟", "45分钟", "60分钟", "1小时以上"];
    function chooseCookingTime() {
      common_vendor.index.showActionSheet({
        itemList: COOKING_TIME_OPTIONS,
        success: (res) => {
          cookingTime.value = COOKING_TIME_OPTIONS[res.tapIndex];
        }
      });
    }
    return (_ctx, _cache) => {
      return common_vendor.e({
        a: common_vendor.p({
          title: "记一笔今天",
          ["safe-area-inset-top"]: true,
          ["custom-style"]: "background-color: transparent !important;"
        }),
        b: `${common_vendor.unref(utils_assets.STATIC_BASE_URL)}/static/guozai/action_03_camera.png`,
        c: isBackfill()
      }, isBackfill() ? {
        d: common_vendor.p({
          name: "calendar",
          size: 30,
          color: "#EF5A3C"
        }),
        e: common_vendor.t(recordDate.value)
      } : {}, {
        f: common_vendor.t(photos.value.length),
        g: common_vendor.f(photos.value, (photo, index, i0) => {
          return common_vendor.e({
            a: photo.localUrl,
            b: photo.uploading
          }, photo.uploading ? {} : {}, {
            c: `将第${index + 1}张照片向前移动`,
            d: index === 0 ? 1 : "",
            e: common_vendor.o(($event) => movePhoto(index, -1), photo.localUrl),
            f: `删除第${index + 1}张照片`,
            g: common_vendor.o(($event) => removePhoto(index), photo.localUrl),
            h: `将第${index + 1}张照片向后移动`,
            i: index === photos.value.length - 1 ? 1 : "",
            j: common_vendor.o(($event) => movePhoto(index, 1), photo.localUrl),
            k: photo.localUrl
          });
        }),
        h: photos.value.length < 9
      }, photos.value.length < 9 ? {
        i: common_vendor.p({
          name: "camera",
          size: 48,
          color: "#EF5A3C"
        }),
        j: common_vendor.t(photos.value.length ? "添加照片" : "拍照或从相册选择"),
        k: common_vendor.o(addImages)
      } : {}, {
        l: dishName.value,
        m: common_vendor.o(($event) => dishName.value = $event.detail.value),
        n: common_vendor.p({
          name: "clock",
          size: 34,
          color: "#EF5A3C"
        }),
        o: common_vendor.t(cookingTime.value),
        p: common_vendor.o(chooseCookingTime),
        q: common_vendor.o(($event) => selectedMood.value = $event),
        r: common_vendor.p({
          ["show-hero"]: false,
          title: "吃完这顿，你是什么心情？",
          modelValue: selectedMood.value
        }),
        s: !isEditing.value
      }, !isEditing.value ? {
        t: common_vendor.o(($event) => feedbackSelections.value = $event),
        v: common_vendor.p({
          options: feedbackOptions,
          disabled: submitting.value,
          modelValue: feedbackSelections.value
        })
      } : {}, {
        w: common_vendor.t(note.value.length),
        x: note.value,
        y: common_vendor.o(($event) => note.value = $event.detail.value),
        z: common_vendor.t(submitting.value ? "正在保存…" : isEditing.value ? "保存这次修改" : "收进我的时光机"),
        A: submitting.value || uploading.value ? 1 : "",
        B: common_vendor.o(publish),
        C: !submitting.value
      }, !submitting.value ? {
        D: common_vendor.t(isEditing.value ? "照片顺序会同步更新" : "以后翻到今天，还能想起这一餐")
      } : {}, {
        E: learningReceipt.value
      }, learningReceipt.value ? {
        F: common_vendor.p({
          title: learningReceipt.value.title,
          items: learningReceipt.value.items,
          variant: learningReceipt.value.status === "LEARNED" ? "learned" : "saved"
        })
      } : {}, {
        G: common_vendor.o(onSuccessConfirm),
        H: common_vendor.o(onSuccessSecondary),
        I: common_vendor.p({
          visible: showSuccess.value,
          title: isEditing.value ? "记录已更新！" : "记录成功！",
          subtitle: isEditing.value ? "这一餐的新样子已经收好" : "今天也好好吃饭了呢",
          ["confirm-text"]: isEditing.value ? "回到这条记录" : "查看这条记录",
          ["secondary-text"]: isEditing.value ? "" : "返回首页"
        })
      });
    };
  }
});
_sfc_defineComponent.__runtimeHooks = 6;
const MiniProgramPage = /* @__PURE__ */ common_vendor._export_sfc(_sfc_defineComponent, [["__scopeId", "data-v-e218755a"]]);
exports.MiniProgramPage = MiniProgramPage;
