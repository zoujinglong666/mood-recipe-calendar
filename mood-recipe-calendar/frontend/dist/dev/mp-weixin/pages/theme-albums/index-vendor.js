"use strict";
const common_vendor = require("../../common/vendor.js");
const utils_assets = require("../../utils/assets.js");
const api_themeAlbums = require("../../api/themeAlbums.js");
const utils_toast = require("../../utils/toast.js");
const __unplugin_components_0 = () => "../../node-modules/@wot-ui/ui/components/wd-navbar/wd-navbar.js";
if (!Array) {
  const _component_wd_navbar = __unplugin_components_0;
  const _component_layout_default_uni = common_vendor.resolveComponent("layout-default-uni");
  (_component_wd_navbar + _component_layout_default_uni)();
}
const _sfc_main = /* @__PURE__ */ common_vendor.defineComponent({
  __name: "index",
  setup(__props) {
    const router = common_vendor.useRouter();
    const loading = common_vendor.ref(true);
    const albums = common_vendor.ref([]);
    common_vendor.onShow(async () => {
      loading.value = true;
      try {
        albums.value = await api_themeAlbums.fetchThemeAlbums();
      } catch (e) {
        utils_toast.toastError(e, "专辑加载失败");
      } finally {
        loading.value = false;
      }
    });
    function openAlbum(album) {
      router.push({ name: "theme-album-detail", params: { sku: album.sku } });
    }
    return (_ctx, _cache) => {
      return common_vendor.e({
        a: common_vendor.o(common_vendor.unref(router).back),
        b: common_vendor.p({
          title: "主题专辑",
          ["left-arrow"]: true,
          ["safe-area-inset-top"]: true,
          ["custom-style"]: "background-color: transparent !important;"
        }),
        c: loading.value
      }, loading.value ? {} : !albums.value.length ? {
        e: `${common_vendor.unref(utils_assets.STATIC_BASE_URL)}/static/guozai/action_08_peek.png`
      } : {
        f: common_vendor.f(albums.value, (album, k0, i0) => {
          return common_vendor.e({
            a: `${common_vendor.unref(utils_assets.STATIC_BASE_URL)}${album.cover}`,
            b: common_vendor.t(album.title),
            c: album.owned
          }, album.owned ? {} : {}, {
            d: common_vendor.t(album.subtitle),
            e: common_vendor.t(album.dishCount),
            f: !album.owned && album.priceFen != null
          }, !album.owned && album.priceFen != null ? {
            g: common_vendor.t((album.priceFen / 100).toFixed(2))
          } : album.owned ? {} : {}, {
            h: album.owned,
            i: album.sku,
            j: `打开专辑${album.title}`,
            k: common_vendor.o(($event) => openAlbum(album), album.sku)
          });
        })
      }, {
        d: !albums.value.length
      });
    };
  }
});
const MiniProgramPage = /* @__PURE__ */ common_vendor._export_sfc(_sfc_main, [["__scopeId", "data-v-4153f83d"]]);
exports.MiniProgramPage = MiniProgramPage;
