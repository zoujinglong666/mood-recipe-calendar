"use strict";
const common_vendor = require("../../common/vendor.js");
const api_records = require("../../api/records.js");
const api_request = require("../../api/request.js");
const utils_chooseImage = require("../../utils/chooseImage.js");
const utils_login = require("../../utils/login.js");
const utils_toast = require("../../utils/toast.js");
if (!Math) {
  (Icon + MoodPicker)();
}
const Icon = () => "../common/Icon.js";
const MoodPicker = () => "../guozai/MoodPicker.js";
const _sfc_main = /* @__PURE__ */ common_vendor.defineComponent({
  __name: "RecordEditorForm",
  props: {
    recordId: {}
  },
  emits: ["saved", "cancel"],
  setup(__props, { emit: __emit }) {
    const props = __props;
    const emit = __emit;
    const loading = common_vendor.ref(true);
    const saving = common_vendor.ref(false);
    const dishName = common_vendor.ref("");
    const selectedMood = common_vendor.ref("");
    const note = common_vendor.ref("");
    const cookingTime = common_vendor.ref("30分钟");
    const recordDate = common_vendor.ref("");
    const photos = common_vendor.ref([]);
    const recipeId = common_vendor.ref();
    const exposureId = common_vendor.ref();
    const uploading = common_vendor.computed(() => photos.value.some((photo) => photo.uploading));
    const COOKING_TIME_OPTIONS = ["10分钟", "20分钟", "30分钟", "45分钟", "60分钟", "1小时以上"];
    async function load() {
      var _a;
      loading.value = true;
      try {
        const record = await api_records.fetchRecord(props.recordId);
        dishName.value = record.dishName || "";
        selectedMood.value = record.moodTag || "";
        note.value = record.note || "";
        cookingTime.value = `${record.cookingTime || 30}分钟`;
        recordDate.value = record.recordDate || "";
        recipeId.value = record.recipeId;
        exposureId.value = record.exposureId;
        photos.value = (((_a = record.imageUrls) == null ? void 0 : _a.length) ? record.imageUrls : [record.imageUrl]).filter(Boolean).map((url) => ({ localUrl: url, remoteUrl: url, uploading: false }));
      } catch (error) {
        utils_toast.toastError(error, "记录加载失败");
        emit("cancel");
      } finally {
        loading.value = false;
      }
    }
    function addImages() {
      if (uploading.value) {
        utils_toast.toast("图片上传中，请稍候");
        return;
      }
      const count = 9 - photos.value.length;
      if (!count) {
        utils_toast.toast("一条记录最多添加 9 张照片");
        return;
      }
      utils_chooseImage.chooseImageFiles({
        count,
        onSelected: uploadImages,
        onFail: () => utils_toast.toast("选择图片失败，请重试")
      });
    }
    async function uploadImages(tempPaths) {
      const startIndex = photos.value.length;
      photos.value.push(...tempPaths.slice(0, 9 - startIndex).map((localUrl) => ({ localUrl, remoteUrl: "", uploading: true })));
      const queue = photos.value.slice(startIndex);
      await Promise.all(queue.map(async (photo) => {
        try {
          photo.remoteUrl = (await api_request.uploadFile(photo.localUrl)).url;
        } catch (error) {
          photos.value = photos.value.filter((item) => item !== photo);
          utils_toast.toastError(error, "有照片上传失败，请重新添加");
        } finally {
          photo.uploading = false;
        }
      }));
      if (queue.some((photo) => photo.remoteUrl))
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
    function chooseCookingTime() {
      common_vendor.index.showActionSheet({
        itemList: COOKING_TIME_OPTIONS,
        success: ({ tapIndex }) => {
          cookingTime.value = COOKING_TIME_OPTIONS[tapIndex];
        }
      });
    }
    async function save() {
      if (saving.value || loading.value)
        return;
      if (!dishName.value.trim()) {
        utils_toast.toast("请输入菜名");
        return;
      }
      if (!selectedMood.value) {
        utils_toast.toast("请选择这顿饭的心情");
        return;
      }
      if (uploading.value) {
        utils_toast.toast("图片上传中，请稍候");
        return;
      }
      if (!photos.value.length) {
        utils_toast.toast("至少保留一张菜品照片");
        return;
      }
      saving.value = true;
      try {
        const openid = await utils_login.ensureLogin();
        const saved = await api_records.updateRecord(props.recordId, {
          openid,
          imageUrl: photos.value[0].remoteUrl || photos.value[0].localUrl,
          imageUrls: photos.value.map((photo) => photo.remoteUrl || photo.localUrl),
          dishName: dishName.value.trim(),
          moodTag: selectedMood.value,
          note: note.value.trim(),
          recipeId: recipeId.value,
          exposureId: exposureId.value,
          cookingTime: Number.parseInt(cookingTime.value, 10) || 30,
          recordDate: recordDate.value
        });
        utils_toast.toastSuccess("这一餐已更新");
        emit("saved", saved);
      } catch (error) {
        utils_toast.toastError(error, "保存失败，请稍后重试");
      } finally {
        saving.value = false;
      }
    }
    common_vendor.onMounted(load);
    return (_ctx, _cache) => {
      return common_vendor.e({
        a: loading.value
      }, loading.value ? {} : common_vendor.e({
        b: common_vendor.t(photos.value.length),
        c: common_vendor.f(photos.value, (photo, index, i0) => {
          return common_vendor.e({
            a: photo.localUrl,
            b: photo.uploading
          }, photo.uploading ? {} : {}, {
            c: `第${index + 1}张照片前移`,
            d: index === 0 ? 1 : "",
            e: common_vendor.o(($event) => movePhoto(index, -1), `${photo.localUrl}-${index}`),
            f: `删除第${index + 1}张照片`,
            g: common_vendor.o(($event) => removePhoto(index), `${photo.localUrl}-${index}`),
            h: `第${index + 1}张照片后移`,
            i: index === photos.value.length - 1 ? 1 : "",
            j: common_vendor.o(($event) => movePhoto(index, 1), `${photo.localUrl}-${index}`),
            k: `${photo.localUrl}-${index}`
          });
        }),
        d: photos.value.length < 9
      }, photos.value.length < 9 ? {
        e: common_vendor.p({
          name: "camera",
          size: 42,
          color: "#EF5A3C"
        }),
        f: common_vendor.o(addImages)
      } : {}, {
        g: dishName.value,
        h: common_vendor.o(common_vendor.m(($event) => dishName.value = $event.detail.value, {
          trim: true
        })),
        i: common_vendor.p({
          name: "calendar",
          size: 32,
          color: "#EF5A3C"
        }),
        j: common_vendor.t(recordDate.value || "选择日期"),
        k: recordDate.value,
        l: common_vendor.o(($event) => recordDate.value = $event.detail.value),
        m: common_vendor.p({
          name: "clock",
          size: 32,
          color: "#EF5A3C"
        }),
        n: common_vendor.t(cookingTime.value),
        o: common_vendor.o(chooseCookingTime),
        p: common_vendor.o(($event) => selectedMood.value = $event),
        q: common_vendor.p({
          ["show-hero"]: false,
          title: "吃完这顿，你是什么心情？",
          modelValue: selectedMood.value
        }),
        r: common_vendor.t(note.value.length),
        s: note.value,
        t: common_vendor.o(($event) => note.value = $event.detail.value),
        v: common_vendor.o(($event) => emit("cancel")),
        w: common_vendor.t(saving.value ? "正在保存…" : "保存修改"),
        x: saving.value || uploading.value ? 1 : "",
        y: common_vendor.o(save)
      }));
    };
  }
});
const Component = /* @__PURE__ */ common_vendor._export_sfc(_sfc_main, [["__scopeId", "data-v-02ce3912"]]);
exports.Component = Component;
