"use strict";
const common_vendor = require("../../../common/vendor.js");
const api_admin = require("../../../api/admin.js");
const api_request = require("../../../api/request.js");
const composables_useNavBar = require("../../../composables/useNavBar.js");
const utils_toast = require("../../../utils/toast.js");
const utils_chooseImage = require("../../../utils/chooseImage.js");
const __unplugin_components_0 = () => "../../../node-modules/@wot-ui/ui/components/wd-navbar/wd-navbar.js";
if (!Array) {
  const _component_wd_navbar = __unplugin_components_0;
  const _component_layout_default_uni = common_vendor.resolveComponent("layout-default-uni");
  (_component_wd_navbar + _component_layout_default_uni)();
}
const _sfc_main = /* @__PURE__ */ common_vendor.defineComponent({
  __name: "edit",
  setup(__props) {
    const router = common_vendor.useRouter();
    const route = common_vendor.useRoute();
    const id = common_vendor.ref(0);
    common_vendor.onLoad((options) => {
      var _a, _b;
      const raw = (options == null ? void 0 : options.id) ?? ((_a = route.query) == null ? void 0 : _a.id) ?? ((_b = route.params) == null ? void 0 : _b.id);
      id.value = Number(raw);
    });
    const recipe = common_vendor.ref(null);
    const saving = common_vendor.ref(false);
    const uploading = common_vendor.ref(false);
    const form = common_vendor.ref({
      name: "",
      description: "",
      image: "",
      ingredients: "",
      steps: "",
      cookingTime: 0,
      difficulty: "",
      moodTags: "",
      taste: "",
      mealType: "",
      tags: "",
      seasonings: "",
      season: ""
    });
    common_vendor.onMounted(async () => {
      if (!id.value || Number.isNaN(id.value)) {
        utils_toast.toastError("缺少菜谱 ID，请从列表进入");
        setTimeout(() => router.back(), 1200);
        return;
      }
      try {
        recipe.value = await api_admin.adminRecipeDetail(id.value);
        Object.assign(form.value, {
          name: recipe.value.name || "",
          description: recipe.value.description || "",
          image: recipe.value.image || "",
          ingredients: recipe.value.ingredients || "",
          steps: recipe.value.steps || "",
          cookingTime: recipe.value.cookingTime || 0,
          difficulty: recipe.value.difficulty || "",
          moodTags: recipe.value.moodTags || "",
          taste: recipe.value.taste || "",
          mealType: recipe.value.mealType || "",
          tags: recipe.value.tags || "",
          seasonings: recipe.value.seasonings || "",
          season: recipe.value.season || ""
        });
      } catch (e) {
        utils_toast.toastError(e.message || "加载失败");
      }
    });
    async function onUploadImage() {
      utils_chooseImage.chooseImageFile({
        onSelected: async (tempPath) => {
          uploading.value = true;
          try {
            const uploadRes = await api_request.uploadFile(tempPath, "image");
            form.value.image = uploadRes.url;
            utils_toast.toastSuccess("图片上传成功");
          } catch (e) {
            utils_toast.toastError(e.message || "上传失败");
          } finally {
            uploading.value = false;
          }
        },
        onFail: () => utils_toast.toastError("选择图片失败")
      });
    }
    async function onSave() {
      if (!form.value.name.trim()) {
        utils_toast.toastError("菜名不能为空");
        return;
      }
      saving.value = true;
      try {
        await api_admin.adminRecipeUpdate(id.value, { ...form.value });
        utils_toast.toastSuccess("保存成功");
        setTimeout(() => router.back(), 800);
      } catch (e) {
        utils_toast.toastError(e.message || "保存失败");
      } finally {
        saving.value = false;
      }
    }
    function onDelete() {
      common_vendor.index.showModal({
        title: "确认删除",
        content: `确定删除「${form.value.name}」吗？此操作不可恢复。`,
        confirmColor: "#D94841",
        success: async (res) => {
          if (!res.confirm)
            return;
          try {
            await api_admin.adminRecipeDelete(id.value);
            utils_toast.toastSuccess("已删除");
            setTimeout(() => router.back(), 800);
          } catch (e) {
            utils_toast.toastError(e.message || "删除失败");
          }
        }
      });
    }
    return (_ctx, _cache) => {
      return common_vendor.e({
        a: recipe.value
      }, recipe.value ? common_vendor.e({
        b: common_vendor.o(common_vendor.unref(composables_useNavBar.navBack)),
        c: common_vendor.p({
          title: "编辑菜谱",
          ["left-arrow"]: true,
          ["safe-area-inset-top"]: true,
          ["custom-style"]: "background-color: transparent !important;"
        }),
        d: form.value.image
      }, form.value.image ? {
        e: common_vendor.unref(api_request.resolveAssetUrl)(form.value.image)
      } : {}, {
        f: common_vendor.t(uploading.value ? "上传中..." : "更换图片"),
        g: uploading.value,
        h: common_vendor.o(onUploadImage),
        i: form.value.image && form.value.image.startsWith("/static/")
      }, form.value.image && form.value.image.startsWith("/static/") ? {} : {}, {
        j: form.value.name,
        k: common_vendor.o(($event) => form.value.name = $event.detail.value),
        l: form.value.description,
        m: common_vendor.o(($event) => form.value.description = $event.detail.value),
        n: form.value.ingredients,
        o: common_vendor.o(($event) => form.value.ingredients = $event.detail.value),
        p: form.value.steps,
        q: common_vendor.o(($event) => form.value.steps = $event.detail.value),
        r: form.value.cookingTime,
        s: common_vendor.o(common_vendor.m(($event) => form.value.cookingTime = $event.detail.value, {
          number: true
        })),
        t: form.value.difficulty,
        v: common_vendor.o(($event) => form.value.difficulty = $event.detail.value),
        w: form.value.taste,
        x: common_vendor.o(($event) => form.value.taste = $event.detail.value),
        y: form.value.mealType,
        z: common_vendor.o(($event) => form.value.mealType = $event.detail.value),
        A: form.value.moodTags,
        B: common_vendor.o(($event) => form.value.moodTags = $event.detail.value),
        C: form.value.tags,
        D: common_vendor.o(($event) => form.value.tags = $event.detail.value),
        E: form.value.seasonings,
        F: common_vendor.o(($event) => form.value.seasonings = $event.detail.value),
        G: common_vendor.t(saving.value ? "保存中..." : "保存"),
        H: saving.value,
        I: common_vendor.o(onSave),
        J: common_vendor.o(onDelete)
      }) : {});
    };
  }
});
const MiniProgramPage = /* @__PURE__ */ common_vendor._export_sfc(_sfc_main, [["__scopeId", "data-v-2cace1c7"]]);
exports.MiniProgramPage = MiniProgramPage;
