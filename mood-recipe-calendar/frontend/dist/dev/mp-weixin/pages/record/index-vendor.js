"use strict";
const common_vendor = require("../../common/vendor.js");
require("../../composables/useNavBar.js");
const utils_assets = require("../../utils/assets.js");
const utils_login = require("../../utils/login.js");
const utils_toast = require("../../utils/toast.js");
const api_records = require("../../api/records.js");
const api_request = require("../../api/request.js");
const utils_chooseImage = require("../../utils/chooseImage.js");
const utils_cookingDraft = require("../../utils/cookingDraft.js");
const __unplugin_components_0 = () => "../../node-modules/@wot-ui/ui/components/wd-navbar/wd-navbar.js";
if (!Array) {
  const _component_wd_navbar = __unplugin_components_0;
  const _component_layout_tabbar_uni = common_vendor.resolveComponent("layout-tabbar-uni");
  (_component_wd_navbar + _component_layout_tabbar_uni)();
}
if (!Math) {
  (Icon + MoodPicker + SuccessModal)();
}
const Icon = () => "../../components/common/Icon.js";
const SuccessModal = () => "../../components/guozai/SuccessModal.js";
const MoodPicker = () => "../../components/guozai/MoodPicker.js";
const _sfc_main = /* @__PURE__ */ common_vendor.defineComponent({
  __name: "index",
  setup(__props) {
    const router = common_vendor.useRouter();
    const dishName = common_vendor.ref("");
    const selectedMood = common_vendor.ref("");
    const note = common_vendor.ref("");
    const cookingTime = common_vendor.ref("30分钟");
    const dishImage = common_vendor.ref("");
    const imageUrl = common_vendor.ref("");
    const recipeId = common_vendor.ref();
    const exposureId = common_vendor.ref();
    const fromRecipe = common_vendor.ref(false);
    const clientRequestId = common_vendor.ref(utils_cookingDraft.createRequestId());
    const savedRecordId = common_vendor.ref();
    const showSuccess = common_vendor.ref(false);
    const submitting = common_vendor.ref(false);
    const uploading = common_vendor.ref(false);
    common_vendor.onShow(() => {
      try {
        const d = common_vendor.index.getStorageSync(utils_cookingDraft.RECORD_DRAFT_KEY);
        if (d) {
          if (d.dish)
            dishName.value = d.dish;
          if (d.mood)
            selectedMood.value = d.mood;
          if (d.recipeId !== void 0 && d.recipeId !== null)
            recipeId.value = String(d.recipeId);
          if (d.exposureId)
            exposureId.value = String(d.exposureId);
          if (d.image) {
            dishImage.value = String(d.image);
            imageUrl.value = String(d.image);
          }
          if (d.cookingTime)
            cookingTime.value = `${Number(d.cookingTime)}分钟`;
          if (d.clientRequestId)
            clientRequestId.value = String(d.clientRequestId);
          fromRecipe.value = d.source === "recipe";
        }
      } catch (e) {
      }
    });
    async function chooseImage() {
      if (uploading.value) {
        utils_toast.toast("图片上传中，请稍候");
        return;
      }
      utils_chooseImage.chooseImageFile({
        onSelected: (tempPath) => {
          uploading.value = true;
          dishImage.value = tempPath;
          uploadImage(tempPath);
        },
        onFail: () => utils_toast.toast("选择图片失败，请重试")
      });
    }
    async function uploadImage(tempPath) {
      try {
        const result = await api_request.uploadFile(tempPath);
        imageUrl.value = result.url;
        utils_toast.toastSuccess("照片已收好");
      } catch (e) {
        utils_toast.toastError(e, "图片上传失败");
        dishImage.value = "";
        imageUrl.value = "";
      } finally {
        uploading.value = false;
      }
    }
    async function publish() {
      if (submitting.value)
        return;
      if (!dishImage.value && !fromRecipe.value) {
        utils_toast.toast("请先上传菜品照片");
        return;
      }
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
        const saved = await api_records.saveRecord({
          openid,
          imageUrl: imageUrl.value || dishImage.value,
          dishName: dishName.value.trim(),
          moodTag: selectedMood.value,
          note: note.value,
          recipeId: recipeId.value,
          exposureId: exposureId.value,
          clientRequestId: clientRequestId.value,
          cookingTime: parseInt(cookingTime.value) || 30
        });
        savedRecordId.value = saved.id;
        common_vendor.index.removeStorageSync(utils_cookingDraft.RECORD_DRAFT_KEY);
        common_vendor.index.removeStorageSync("mrc_companion_message");
        showSuccess.value = true;
      } catch (e) {
        utils_toast.toastError(e, "保存失败");
      } finally {
        submitting.value = false;
      }
    }
    function onSuccessConfirm() {
      showSuccess.value = false;
      if (savedRecordId.value)
        common_vendor.index.setStorageSync("mrc_timeline_record_id", savedRecordId.value);
      dishName.value = "";
      selectedMood.value = "";
      note.value = "";
      cookingTime.value = "30分钟";
      dishImage.value = "";
      imageUrl.value = "";
      recipeId.value = void 0;
      exposureId.value = void 0;
      fromRecipe.value = false;
      clientRequestId.value = utils_cookingDraft.createRequestId();
      savedRecordId.value = void 0;
      router.push({ name: "timeline" });
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
          title: "记录今日伙食",
          ["safe-area-inset-top"]: true,
          ["custom-style"]: "background-color: transparent !important;"
        }),
        b: common_vendor.unref(utils_assets.STATIC_BASE_URL) + "/static/guozai/action_03_camera.png",
        c: dishImage.value
      }, dishImage.value ? common_vendor.e({
        d: dishImage.value,
        e: common_vendor.p({
          name: "camera",
          size: 30,
          color: "#FFFFFF"
        }),
        f: common_vendor.t(uploading.value ? "上传中…" : "更换照片"),
        g: uploading.value
      }, uploading.value ? {} : {}) : {
        h: common_vendor.p({
          name: "camera",
          size: 56,
          color: "#EF5A3C"
        }),
        i: common_vendor.t(fromRecipe.value ? "想换成自己的成品照吗？" : "先拍下今天这道菜"),
        j: common_vendor.t(fromRecipe.value ? "可选 · 不拍也能先收进时光机" : "拍照或从相册选择 · 必填")
      }, {
        k: common_vendor.o(chooseImage),
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
        s: common_vendor.t(note.value.length),
        t: note.value,
        v: common_vendor.o(($event) => note.value = $event.detail.value),
        w: common_vendor.t(submitting.value ? "正在保存…" : "收进我的时光机"),
        x: !submitting.value
      }, !submitting.value ? {} : {}, {
        y: submitting.value || uploading.value ? 1 : "",
        z: common_vendor.o(publish),
        A: common_vendor.o(onSuccessConfirm),
        B: common_vendor.p({
          visible: showSuccess.value,
          title: "记录成功！",
          subtitle: "今天也好好吃饭了呢"
        })
      });
    };
  }
});
const MiniProgramPage = /* @__PURE__ */ common_vendor._export_sfc(_sfc_main, [["__scopeId", "data-v-e218755a"]]);
exports.MiniProgramPage = MiniProgramPage;
