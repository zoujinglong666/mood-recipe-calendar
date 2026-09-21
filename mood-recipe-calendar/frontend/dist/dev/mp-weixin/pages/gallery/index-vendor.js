"use strict";
const common_vendor = require("../../common/vendor.js");
const composables_useNavBar = require("../../composables/useNavBar.js");
const utils_assets = require("../../utils/assets.js");
const utils_login = require("../../utils/login.js");
const utils_toast = require("../../utils/toast.js");
const api_gallery = require("../../api/gallery.js");
const __unplugin_components_0 = () => "../../node-modules/@wot-ui/ui/components/wd-navbar/wd-navbar.js";
if (!Array) {
  const _component_wd_navbar = __unplugin_components_0;
  const _component_layout_default_uni = common_vendor.resolveComponent("layout-default-uni");
  (_component_wd_navbar + _component_layout_default_uni)();
}
if (!Math) {
  (LoadingState + ErrorState + Icon)();
}
const Icon = () => "../../components/common/Icon.js";
const LoadingState = () => "../../components/guozai/LoadingState.js";
const ErrorState = () => "../../components/guozai/ErrorState.js";
const _sfc_main = /* @__PURE__ */ common_vendor.defineComponent({
  __name: "index",
  setup(__props) {
    common_vendor.useRouter();
    const loading = common_vendor.ref(true);
    const error = common_vendor.ref("");
    common_vendor.ref([]);
    common_vendor.ref([]);
    common_vendor.ref(null);
    common_vendor.ref("normal");
    common_vendor.ref(false);
    const checkin = common_vendor.ref({ checkedIn: false, streak: 0, exchangeReady: false, daysToExchange: 30, totalDays: 0 });
    const STICKER_GROUPS = [
      {
        title: "心情表情",
        items: [
          { name: "开心", src: utils_assets.STATIC_BASE_URL + "/static/guozai/mood_01_happy.png" },
          { name: "平静", src: utils_assets.STATIC_BASE_URL + "/static/guozai/mood_02_calm.png" },
          { name: "疲惫", src: utils_assets.STATIC_BASE_URL + "/static/guozai/mood_03_tired.png" },
          { name: "焦虑", src: utils_assets.STATIC_BASE_URL + "/static/guozai/mood_04_anxious.png" },
          { name: "难过", src: utils_assets.STATIC_BASE_URL + "/static/guozai/mood_05_sad.png" },
          { name: "嘴馋", src: utils_assets.STATIC_BASE_URL + "/static/guozai/mood_06_hungry.png" },
          { name: "低落", src: utils_assets.STATIC_BASE_URL + "/static/guozai/mood_07_low.png" },
          { name: "想家", src: utils_assets.STATIC_BASE_URL + "/static/guozai/mood_08_homesick.png" }
        ]
      },
      {
        title: "动作表情",
        items: [
          { name: "端碗", src: utils_assets.STATIC_BASE_URL + "/static/guozai/action_01_bowl.png" },
          { name: "端汤", src: utils_assets.STATIC_BASE_URL + "/static/guozai/action_02_soup.png" },
          { name: "拍照", src: utils_assets.STATIC_BASE_URL + "/static/guozai/action_03_camera.png" },
          { name: "日历", src: utils_assets.STATIC_BASE_URL + "/static/guozai/action_04_calendar.png" },
          { name: "画册", src: utils_assets.STATIC_BASE_URL + "/static/guozai/action_05_album.png" },
          { name: "眼镜", src: utils_assets.STATIC_BASE_URL + "/static/guozai/action_06_glasses.png" },
          { name: "空碗", src: utils_assets.STATIC_BASE_URL + "/static/guozai/action_07_empty.png" },
          { name: "探头", src: utils_assets.STATIC_BASE_URL + "/static/guozai/action_08_peek.png" },
          { name: "庆祝", src: utils_assets.STATIC_BASE_URL + "/static/guozai/action_09_celebrate.png" },
          { name: "思考", src: utils_assets.STATIC_BASE_URL + "/static/guozai/action_10_thinking.png" }
        ]
      },
      {
        title: "状态表情",
        items: [
          { name: "空状态", src: utils_assets.STATIC_BASE_URL + "/static/guozai/state_01_empty.png" },
          { name: "加载中", src: utils_assets.STATIC_BASE_URL + "/static/guozai/state_02_loading.png" },
          { name: "网络错误", src: utils_assets.STATIC_BASE_URL + "/static/guozai/state_03_error.png" }
        ]
      }
    ];
    const progressPct = common_vendor.computed(() => Math.min(100, Math.round(checkin.value.streak / 30 * 100)));
    async function loadData() {
      loading.value = true;
      error.value = "";
      try {
        const openid = await utils_login.ensureLogin();
        checkin.value = await api_gallery.fetchCheckinStatus(openid);
      } catch (e) {
        error.value = e.message || "加载失败";
      } finally {
        loading.value = false;
      }
    }
    common_vendor.onShow(() => {
      loadData();
    });
    async function onCheckin() {
      if (checkin.value.checkedIn)
        return;
      try {
        const openid = await utils_login.ensureLogin();
        checkin.value = await api_gallery.doCheckin(openid);
        utils_toast.toast("签到成功，锅仔陪你吃饭！");
      } catch (e) {
        utils_toast.toastError(e, "签到失败");
      }
    }
    function onDownloadSticker(sticker) {
      common_vendor.index.authorize({
        scope: "scope.writePhotosAlbum",
        success: () => {
          common_vendor.index.saveImageToPhotosAlbum({
            filePath: sticker.src,
            success: () => utils_toast.toastSuccess(`已保存「${sticker.name}」到相册`),
            fail: () => {
              common_vendor.index.getImageInfo({
                src: sticker.src,
                success: (info) => {
                  common_vendor.index.saveImageToPhotosAlbum({
                    filePath: info.path,
                    success: () => utils_toast.toastSuccess(`已保存「${sticker.name}」`),
                    fail: () => utils_toast.toast("保存失败，请检查相册权限")
                  });
                },
                fail: () => utils_toast.toast("保存失败")
              });
            }
          });
        },
        fail: () => utils_toast.toast("需要相册权限才能下载")
      });
    }
    return (_ctx, _cache) => {
      return common_vendor.e({
        a: common_vendor.o(common_vendor.unref(composables_useNavBar.navBack)),
        b: common_vendor.p({
          title: "锅仔形象馆",
          ["left-arrow"]: true,
          ["safe-area-inset-top"]: true,
          ["custom-style"]: "background-color: transparent !important;"
        }),
        c: loading.value
      }, loading.value ? {
        d: common_vendor.p({
          text: "锅仔正在布置形象馆..."
        })
      } : error.value ? {
        f: common_vendor.o(loadData)
      } : common_vendor.e({
        g: checkin.value.exchangeReady
      }, checkin.value.exchangeReady ? {} : {}, {
        h: common_vendor.t(checkin.value.streak),
        i: progressPct.value + "%",
        j: checkin.value.exchangeReady ? common_vendor.unref(utils_assets.STATIC_BASE_URL) + "/static/guozai/action_09_celebrate.png" : checkin.value.checkedIn ? common_vendor.unref(utils_assets.STATIC_BASE_URL) + "/static/guozai/mood_01_happy.png" : common_vendor.unref(utils_assets.STATIC_BASE_URL) + "/static/guozai/action_08_peek.png",
        k: common_vendor.t(checkin.value.checkedIn ? "今日已签到" : "签到"),
        l: checkin.value.checkedIn ? 1 : "",
        m: checkin.value.exchangeReady ? 1 : "",
        n: common_vendor.o(onCheckin),
        o: common_vendor.unref(utils_assets.STATIC_BASE_URL) + "/static/guozai/mood_06_hungry.png",
        p: common_vendor.f(STICKER_GROUPS, (group, k0, i0) => {
          return {
            a: common_vendor.t(group.title),
            b: common_vendor.f(group.items, (s, k1, i1) => {
              return {
                a: s.src,
                b: common_vendor.t(s.name),
                c: s.name,
                d: common_vendor.o(($event) => onDownloadSticker(s), s.name)
              };
            }),
            c: group.title
          };
        })
      }, {}, {}, {
        x: common_vendor.unref(utils_assets.STATIC_BASE_URL) + "/static/guozai/mood_02_calm.png"
      }), {
        e: error.value
      }, {});
    };
  }
});
const MiniProgramPage = /* @__PURE__ */ common_vendor._export_sfc(_sfc_main, [["__scopeId", "data-v-5a43f0bf"]]);
exports.MiniProgramPage = MiniProgramPage;
