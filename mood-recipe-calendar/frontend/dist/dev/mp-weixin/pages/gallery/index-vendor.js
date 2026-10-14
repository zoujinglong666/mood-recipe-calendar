"use strict";
const common_vendor = require("../../common/vendor.js");
const composables_useNavBar = require("../../composables/useNavBar.js");
const utils_assets = require("../../utils/assets.js");
const utils_login = require("../../utils/login.js");
const utils_toast = require("../../utils/toast.js");
const api_gallery = require("../../api/gallery.js");
const api_achievements = require("../../api/achievements.js");
const api_virtualCommerce = require("../../api/virtualCommerce.js");
const __unplugin_components_0 = () => "../../node-modules/@wot-ui/ui/components/wd-navbar/wd-navbar.js";
if (!Array) {
  const _component_wd_navbar = __unplugin_components_0;
  const _component_layout_default_uni = common_vendor.resolveComponent("layout-default-uni");
  (_component_wd_navbar + _component_layout_default_uni)();
}
if (!Math) {
  (LoadingState + ErrorState + Icon + SuccessModal)();
}
const Icon = () => "../../components/common/Icon.js";
const LoadingState = () => "../../components/guozai/LoadingState.js";
const ErrorState = () => "../../components/guozai/ErrorState.js";
const SuccessModal = () => "../../components/guozai/SuccessModal.js";
const _sfc_defineComponent = common_vendor.defineComponent({
  __name: "index",
  setup(__props) {
    common_vendor.onShareAppMessage(() => {
      var _a;
      const pages = getCurrentPages();
      const route = ((_a = pages[pages.length - 1]) == null ? void 0 : _a.route) || "";
      return { title: "锅仔 · 按心情帮你决定今天吃什么", path: `/${route}` };
    });
    common_vendor.onShareTimeline(() => ({ title: "锅仔 · 按心情帮你决定今天吃什么" }));
    common_vendor.useRouter();
    const loading = common_vendor.ref(true);
    const error = common_vendor.ref("");
    common_vendor.ref([]);
    const orders = common_vendor.ref([]);
    const productTitleMap = common_vendor.ref({});
    common_vendor.ref(null);
    common_vendor.ref("normal");
    common_vendor.ref(false);
    const checkin = common_vendor.ref({ checkedIn: false, streak: 0, exchangeReady: false, daysToExchange: 30, totalDays: 0 });
    const checking = common_vendor.ref(false);
    const showCheckinModal = common_vendor.ref(false);
    const achievements = common_vendor.ref(null);
    const achievementPct = common_vendor.computed(() => {
      const a = achievements.value;
      if (!a || !a.total)
        return 0;
      return Math.min(100, Math.round(a.unlockedCount / a.total * 100));
    });
    function showAchievement(item) {
      common_vendor.index.showModal({
        title: `${item.emoji} ${item.name}`,
        content: `${item.unlocked ? "已解锁 · " : "还没解锁 · "}${item.desc}`,
        showCancel: false,
        confirmText: item.unlocked ? "真棒" : "加油"
      });
    }
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
        await utils_login.ensureLogin();
        const [ck, prods, vOrders, achv] = await Promise.all([
          api_gallery.fetchCheckinStatus(),
          api_virtualCommerce.fetchVirtualProducts().catch(() => []),
          api_virtualCommerce.fetchVirtualOrders().catch(() => []),
          api_achievements.fetchAchievements().catch(() => null)
        ]);
        checkin.value = ck;
        achievements.value = achv;
        productTitleMap.value = Object.fromEntries(prods.map((p) => [p.sku, p.title]));
        orders.value = vOrders.map((o) => ({
          orderNo: o.orderNo,
          sku: o.sku,
          title: productTitleMap.value[o.sku] || o.sku,
          amountFen: o.amountFen,
          status: o.status
        }));
      } catch (e) {
        error.value = e.message || "加载失败";
      } finally {
        loading.value = false;
      }
    }
    common_vendor.onShow(() => {
      var _a;
      common_vendor.index.removeStorageSync("mrc_achv_unread");
      loadData();
      const pages = getCurrentPages();
      const query = ((_a = pages[pages.length - 1]) == null ? void 0 : _a.options) || {};
      if (query.focus === "achievements") {
        setTimeout(() => {
          common_vendor.index.pageScrollTo({ selector: "#achvSection", duration: 300 });
        }, 360);
      }
      api_achievements.checkAchievements().then((res) => {
        const codes = (res == null ? void 0 : res.newlyUnlocked) || [];
        if (!codes.length || !achievements.value)
          return;
        const names = codes.map((code) => {
          var _a2;
          return (_a2 = achievements.value) == null ? void 0 : _a2.achievements.find((item) => item.code === code);
        }).filter(Boolean).map((item) => `${item.emoji} ${item.name}`);
        if (!names.length)
          return;
        common_vendor.index.showModal({
          title: "锅仔播报",
          content: `解锁新成就：${names.join("、")}！去成就墙看看吧`,
          showCancel: false,
          confirmText: "好耶"
        });
      }).catch(() => {
      });
    });
    const orderStatusText = {
      PENDING: "待支付",
      PAID: "已支付",
      DELIVERED: "已发货",
      REFUNDED: "已退款",
      CANCELLED: "已取消"
    };
    async function onCheckin() {
      if (checkin.value.checkedIn || checking.value)
        return;
      checking.value = true;
      try {
        await utils_login.ensureLogin();
        checkin.value = await api_gallery.doCheckin();
        showCheckinModal.value = true;
      } catch (e) {
        utils_toast.toastError(e, "签到失败");
      } finally {
        checking.value = false;
      }
    }
    function onDownloadSticker(sticker) {
      common_vendor.index.downloadFile({
        url: sticker.src,
        success: (res) => {
          if (res.statusCode === 200 && res.tempFilePath) {
            common_vendor.index.saveImageToPhotosAlbum({
              filePath: res.tempFilePath,
              success: () => utils_toast.toastSuccess(`已保存「${sticker.name}」到相册`),
              fail: () => utils_toast.toast("保存失败，请检查相册权限")
            });
          } else {
            utils_toast.toast("下载失败，请稍后重试");
          }
        },
        fail: () => utils_toast.toast("下载失败，请检查网络")
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
        o: achievements.value
      }, achievements.value ? {
        p: common_vendor.t(achievements.value.unlockedCount),
        q: common_vendor.t(achievements.value.total),
        r: achievementPct.value + "%",
        s: common_vendor.f(achievements.value.achievements, (item, k0, i0) => {
          return {
            a: common_vendor.t(item.unlocked ? item.emoji : "🔒"),
            b: common_vendor.t(item.name),
            c: item.code,
            d: !item.unlocked ? 1 : "",
            e: `${item.name}，${item.unlocked ? "已解锁" : "未解锁"}，${item.desc}`,
            f: common_vendor.o(($event) => showAchievement(item), item.code)
          };
        })
      } : {}, {
        t: common_vendor.unref(utils_assets.STATIC_BASE_URL) + "/static/guozai/mood_06_hungry.png",
        v: common_vendor.f(STICKER_GROUPS, (group, k0, i0) => {
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
      }, {}, {
        z: orders.value.length === 0
      }, orders.value.length === 0 ? {
        A: common_vendor.unref(utils_assets.STATIC_BASE_URL) + "/static/guozai/action_07_empty.png"
      } : {}, {
        B: common_vendor.f(orders.value, (o, k0, i0) => {
          return {
            a: common_vendor.t(o.title),
            b: common_vendor.t(o.orderNo),
            c: common_vendor.t((o.amountFen / 100).toFixed(2)),
            d: common_vendor.t(orderStatusText[o.status] || o.status),
            e: common_vendor.n("order-card__status--" + o.status.toLowerCase()),
            f: o.orderNo
          };
        }),
        C: common_vendor.unref(utils_assets.STATIC_BASE_URL) + "/static/guozai/mood_01_happy.png",
        D: common_vendor.unref(utils_assets.STATIC_BASE_URL) + "/static/guozai/mood_02_calm.png"
      }), {
        e: error.value
      }, {}, {
        V: common_vendor.o(($event) => showCheckinModal.value = false),
        W: common_vendor.p({
          visible: showCheckinModal.value,
          title: "签到成功！",
          subtitle: `今日锅仔对话 +3 轮，已连续签到 ${checkin.value.streak} 天`,
          ["confirm-text"]: "好的"
        })
      });
    };
  }
});
_sfc_defineComponent.__runtimeHooks = 6;
const MiniProgramPage = /* @__PURE__ */ common_vendor._export_sfc(_sfc_defineComponent, [["__scopeId", "data-v-5a43f0bf"]]);
exports.MiniProgramPage = MiniProgramPage;
