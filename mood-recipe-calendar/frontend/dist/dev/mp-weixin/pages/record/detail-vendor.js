"use strict";
const common_vendor = require("../../common/vendor.js");
const api_records = require("../../api/records.js");
const api_recordPoster = require("../../api/recordPoster.js");
const utils_albumShare = require("../../utils/albumShare.js");
const composables_useNavBar = require("../../composables/useNavBar.js");
const utils_login = require("../../utils/login.js");
const utils_toast = require("../../utils/toast.js");
const stores_user = require("../../stores/user.js");
const __unplugin_components_2 = () => "../../node-modules/@wot-ui/ui/components/wd-image-preview/wd-image-preview.js";
const __unplugin_components_1 = () => "../../node-modules/@wot-ui/ui/components/wd-icon/wd-icon.js";
const __unplugin_components_0 = () => "../../node-modules/@wot-ui/ui/components/wd-navbar/wd-navbar.js";
if (!Array) {
  const _component_wd_navbar = __unplugin_components_0;
  const _component_wd_icon = __unplugin_components_1;
  const _component_wd_image_preview = __unplugin_components_2;
  const _component_layout_default_uni = common_vendor.resolveComponent("layout-default-uni");
  (_component_wd_navbar + _component_wd_icon + _component_wd_image_preview + _component_layout_default_uni)();
}
if (!Math) {
  (LoadingState + EmptyState + GuozaiButton + GuozaiWotButton)();
}
const EmptyState = () => "../../components/guozai/EmptyState.js";
const GuozaiButton = () => "../../components/guozai/GuozaiButton.js";
const GuozaiWotButton = () => "../../components/guozai/GuozaiWotButton.js";
const LoadingState = () => "../../components/guozai/LoadingState.js";
const _sfc_defineComponent = common_vendor.defineComponent({
  __name: "detail",
  setup(__props) {
    common_vendor.onShareAppMessage(() => {
      const sharer = userStore.openid;
      const path = `/pages/record/detail${sharer ? `?sharer=${encodeURIComponent(sharer)}` : ""}`;
      const thumb = savedPosterPath.value || photos.value[0];
      return {
        title: record.value ? `我记下了「${record.value.dishName}」，锅仔帮我留住这顿饭` : "锅仔 · 记录今天的食光",
        path,
        ...thumb ? { imageUrl: thumb } : {}
      };
    });
    common_vendor.onShareTimeline(() => {
      const sharer = userStore.openid;
      const thumb = savedPosterPath.value || photos.value[0];
      return {
        title: record.value ? `我记下了「${record.value.dishName}」，锅仔帮我留住这顿饭` : "锅仔 · 记录今天的食光",
        query: sharer ? `sharer=${encodeURIComponent(sharer)}` : "",
        ...thumb ? { imageUrl: thumb } : {}
      };
    });
    const router = common_vendor.useRouter();
    const route = common_vendor.useRoute();
    const { previewImage } = common_vendor.useImagePreview();
    const loading = common_vendor.ref(true);
    const record = common_vendor.ref();
    const userStore = stores_user.useUserStore();
    const posterMode = common_vendor.ref("detail");
    const backendPosterUrl = common_vendor.ref("");
    const generatingPoster = common_vendor.ref(false);
    const exportingPoster = common_vendor.ref(false);
    const savedPosterPath = common_vendor.ref("");
    const isMember = common_vendor.computed(() => {
      var _a;
      return ((_a = userStore.userInfo) == null ? void 0 : _a.isMember) === 1 && !!userStore.userInfo.memberExpire && new Date(userStore.userInfo.memberExpire).getTime() > Date.now();
    });
    const photos = common_vendor.computed(() => {
      var _a, _b, _c;
      return ((_b = (_a = record.value) == null ? void 0 : _a.imageUrls) == null ? void 0 : _b.length) ? record.value.imageUrls : ((_c = record.value) == null ? void 0 : _c.imageUrl) ? [record.value.imageUrl] : [];
    });
    const posterDate = common_vendor.computed(() => {
      var _a;
      const [year = "", month = "", day = ""] = (((_a = record.value) == null ? void 0 : _a.recordDate) || "").split("-");
      return { year, month, day };
    });
    function photoClass() {
      return `record-detail__photos--${Math.min(Math.max(photos.value.length, 1), 9)}`;
    }
    function posterPhotoClass() {
      return `record-detail__poster-grid--${Math.min(Math.max(photos.value.length, 1), 9)}`;
    }
    async function selectMode(mode) {
      if (mode !== "server") {
        posterMode.value = mode;
        return;
      }
      if (!isMember.value) {
        common_vendor.index.showModal({ title: "会员专享模式", content: "精装海报由锅仔为你排版生成，可保存分享，开通会员后即可使用。", confirmText: "查看会员", success: ({ confirm }) => {
          if (confirm)
            router.push({ name: "membership" });
        } });
        return;
      }
      posterMode.value = "server";
      if (backendPosterUrl.value || generatingPoster.value || !record.value)
        return;
      generatingPoster.value = true;
      try {
        backendPosterUrl.value = (await api_recordPoster.generateRecordPoster(record.value.id)).url;
      } catch (error) {
        utils_toast.toastError(error, "海报生成失败");
      } finally {
        generatingPoster.value = false;
      }
    }
    function preview(index) {
      if (!photos.value.length)
        return;
      previewImage({ images: photos.value, startPosition: index, closeOnClick: true, loop: photos.value.length > 1 });
    }
    async function buildPoster() {
      if (!record.value)
        return "";
      if (savedPosterPath.value)
        return savedPosterPath.value;
      return await utils_albumShare.exportRecordShare({
        dishName: record.value.dishName,
        moodTag: record.value.moodTag,
        note: record.value.note,
        recordDate: record.value.recordDate,
        cookingTime: record.value.cookingTime,
        photos: photos.value.filter(Boolean)
      });
    }
    async function savePoster() {
      if (!record.value || exportingPoster.value)
        return;
      exportingPoster.value = true;
      common_vendor.index.showLoading({ title: "正在排版…", mask: true });
      try {
        const path = await buildPoster();
        savedPosterPath.value = path;
        await utils_albumShare.saveShareImage(path, `${record.value.dishName}-食光.png`);
        utils_toast.toastSuccess("海报已保存到相册");
      } catch (e) {
        utils_toast.toastError(e, "海报保存失败，请授权相册后重试");
      } finally {
        common_vendor.index.hideLoading();
        exportingPoster.value = false;
      }
    }
    async function sharePoster() {
      if (!record.value || exportingPoster.value)
        return;
      exportingPoster.value = true;
      common_vendor.index.showLoading({ title: "正在生成…", mask: true });
      try {
        const path = await buildPoster();
        savedPosterPath.value = path;
        common_vendor.index.showShareImageMenu({ path });
      } catch (e) {
        utils_toast.toastError(e, "海报生成失败");
      } finally {
        common_vendor.index.hideLoading();
        exportingPoster.value = false;
      }
    }
    function edit() {
      if (!record.value)
        return;
      router.push({ name: "record-edit", params: { id: String(record.value.id) } });
    }
    async function remove() {
      if (!record.value)
        return;
      const confirmation = await common_vendor.index.showModal({ title: "删除这条记录？", content: "删除后无法恢复。", confirmColor: "#D94A43" });
      if (!confirmation.confirm)
        return;
      try {
        await utils_login.ensureLogin();
        await api_records.deleteRecord(record.value.id);
        utils_toast.toastSuccess("记录已删除");
        router.back();
      } catch (e) {
        utils_toast.toastError(e, "删除失败");
      }
    }
    const recordId = common_vendor.ref(0);
    common_vendor.onLoad((options) => {
      var _a, _b;
      const raw = (options == null ? void 0 : options.id) ?? ((_a = route == null ? void 0 : route.query) == null ? void 0 : _a.id) ?? ((_b = route == null ? void 0 : route.params) == null ? void 0 : _b.id);
      recordId.value = Number(raw);
    });
    async function load() {
      const id = recordId.value;
      if (!id) {
        utils_toast.toastError(new Error("记录不存在"), "打不开这条记录");
        router.back();
        return;
      }
      loading.value = true;
      try {
        record.value = await api_records.fetchRecord(id);
        backendPosterUrl.value = record.value.posterUrl || "";
        savedPosterPath.value = "";
      } catch (e) {
        utils_toast.toastError(e, "记录加载失败");
      } finally {
        loading.value = false;
      }
    }
    common_vendor.onShow(load);
    return (_ctx, _cache) => {
      return common_vendor.e({
        a: common_vendor.o(common_vendor.unref(composables_useNavBar.navBack)),
        b: common_vendor.p({
          title: "这一餐的食光",
          ["left-arrow"]: true,
          ["safe-area-inset-top"]: true,
          ["custom-style"]: "background-color: transparent !important;"
        }),
        c: loading.value
      }, loading.value ? {
        d: common_vendor.p({
          text: "锅仔正在打开这段食光…"
        })
      } : !record.value ? {
        f: common_vendor.o(($event) => common_vendor.unref(router).push({
          name: "timeline"
        })),
        g: common_vendor.p({
          title: "这条记录不见了",
          text: "可能已被删除，回到时光机看看其他回忆吧。",
          ["action-text"]: "回到时光机"
        })
      } : common_vendor.e({
        h: posterMode.value === "detail" ? 1 : "",
        i: posterMode.value === "detail",
        j: common_vendor.o(($event) => selectMode("detail")),
        k: posterMode.value === "poster" ? 1 : "",
        l: posterMode.value === "poster",
        m: common_vendor.o(($event) => selectMode("poster")),
        n: isMember.value
      }, isMember.value ? {
        o: posterMode.value === "server" ? 1 : "",
        p: posterMode.value === "server",
        q: common_vendor.o(($event) => selectMode("server"))
      } : {}, {
        r: posterMode.value === "detail"
      }, posterMode.value === "detail" ? common_vendor.e({
        s: common_vendor.t(record.value.moodTag || "这一餐"),
        t: common_vendor.t(record.value.dishName),
        v: common_vendor.t(record.value.recordDate),
        w: common_vendor.t(record.value.cookingTime || 30),
        x: common_vendor.f(photos.value, (photo, index, i0) => {
          return {
            a: photo,
            b: photo,
            c: `预览第${index + 1}张照片`,
            d: common_vendor.o(($event) => preview(index), photo)
          };
        }),
        y: photos.value.length > 1
      }, photos.value.length > 1 ? {
        z: common_vendor.t(photos.value.length)
      } : {}, {
        A: common_vendor.n(photoClass()),
        B: record.value.note
      }, record.value.note ? {
        C: common_vendor.t(record.value.note)
      } : {}) : posterMode.value === "poster" ? common_vendor.e({
        E: common_vendor.t(posterDate.value.day || "01"),
        F: common_vendor.t(posterDate.value.year || "TODAY"),
        G: common_vendor.t(record.value.dishName),
        H: common_vendor.t(posterDate.value.month || "--"),
        I: common_vendor.t(posterDate.value.day || "--"),
        J: common_vendor.t(record.value.cookingTime || 30),
        K: common_vendor.t(record.value.moodTag || "这一餐"),
        L: common_vendor.f(photos.value, (photo, index, i0) => {
          return {
            a: `poster-${photo}`,
            b: photo,
            c: `预览第${index + 1}张照片`,
            d: common_vendor.o(($event) => preview(index), `poster-${photo}`)
          };
        }),
        M: photos.value.length > 1
      }, photos.value.length > 1 ? {
        N: common_vendor.t(photos.value.length)
      } : {}, {
        O: common_vendor.n(posterPhotoClass()),
        P: common_vendor.t(record.value.note || "把今天的味道收好，下一次回来继续。"),
        Q: common_vendor.t(posterDate.value.year || ""),
        R: common_vendor.t(posterDate.value.month || "")
      }) : common_vendor.e({
        S: generatingPoster.value
      }, generatingPoster.value ? {} : backendPosterUrl.value ? {
        U: backendPosterUrl.value,
        V: common_vendor.o(($event) => common_vendor.unref(previewImage)({
          images: [backendPosterUrl.value],
          startPosition: 0
        }))
      } : {
        W: common_vendor.o(($event) => selectMode("server")),
        X: common_vendor.p({
          variant: "primary"
        })
      }, {
        T: backendPosterUrl.value
      }), {
        D: posterMode.value === "poster",
        Y: common_vendor.o(savePoster),
        Z: common_vendor.p({
          type: "primary",
          size: "medium",
          block: true,
          round: true,
          icon: "download",
          loading: exportingPoster.value
        }),
        aa: common_vendor.o(sharePoster),
        ab: common_vendor.p({
          type: "primary",
          variant: "plain",
          size: "medium",
          block: true,
          round: true,
          icon: "share-alt",
          disabled: exportingPoster.value
        }),
        ac: common_vendor.o(edit),
        ad: common_vendor.p({
          type: "primary",
          variant: "plain",
          size: "medium",
          block: true,
          round: true,
          icon: "edit"
        }),
        ae: common_vendor.p({
          name: "delete",
          size: "22rpx",
          color: "#B3A296"
        }),
        af: common_vendor.o(remove)
      }), {
        e: !record.value
      });
    };
  }
});
_sfc_defineComponent.__runtimeHooks = 6;
const MiniProgramPage = /* @__PURE__ */ common_vendor._export_sfc(_sfc_defineComponent, [["__scopeId", "data-v-ab99db5b"]]);
exports.MiniProgramPage = MiniProgramPage;
