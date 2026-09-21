"use strict";
const common_vendor = require("../../common/vendor.js");
const api_records = require("../../api/records.js");
const composables_useNavBar = require("../../composables/useNavBar.js");
const utils_assets = require("../../utils/assets.js");
const stores_user = require("../../stores/user.js");
const utils_login = require("../../utils/login.js");
const utils_toast = require("../../utils/toast.js");
const api_request = require("../../api/request.js");
const api_auth = require("../../api/auth.js");
const utils_chooseImage = require("../../utils/chooseImage.js");
const __unplugin_components_0 = () => "../../node-modules/@wot-ui/ui/components/wd-icon/wd-icon.js";
if (!Array) {
  const _component_wd_icon = __unplugin_components_0;
  const _component_layout_tabbar_uni = common_vendor.resolveComponent("layout-tabbar-uni");
  (_component_wd_icon + _component_layout_tabbar_uni)();
}
const _sfc_main = /* @__PURE__ */ common_vendor.defineComponent({
  __name: "index",
  setup(__props) {
    const router = common_vendor.useRouter();
    const userStore = stores_user.useUserStore();
    const nav = composables_useNavBar.useNavBar();
    const loading = common_vendor.ref(true);
    const avatarUpdating = common_vendor.ref(false);
    const stats = common_vendor.ref({ totalRecords: 0, totalDays: 0, currentStreak: 0, topDishes: [] });
    const history = common_vendor.ref([]);
    const activeMembership = common_vendor.computed(() => {
      var _a, _b;
      return ((_a = userStore.userInfo) == null ? void 0 : _a.isMember) === 1 && Boolean((_b = userStore.userInfo) == null ? void 0 : _b.memberExpire) && new Date(userStore.userInfo.memberExpire).getTime() > Date.now();
    });
    const MOOD_IMG_MAP = {
      开心: `${utils_assets.STATIC_BASE_URL}
/static/guozai/mood_01_happy.png`,
      平静: `${utils_assets.STATIC_BASE_URL}/static/guozai/mood_02_calm.png`,
      疲惫: `${utils_assets.STATIC_BASE_URL}/static/guozai/mood_03_tired.png`,
      焦虑: `${utils_assets.STATIC_BASE_URL}/static/guozai/mood_04_anxious.png`,
      难过: `${utils_assets.STATIC_BASE_URL}/static/guozai/mood_05_sad.png`,
      嘴馋: `${utils_assets.STATIC_BASE_URL}/static/guozai/mood_06_hungry.png`,
      低落: `${utils_assets.STATIC_BASE_URL}/static/guozai/mood_07_low.png`,
      想家: `${utils_assets.STATIC_BASE_URL}/static/guozai/mood_08_homesick.png`
    };
    async function loadData() {
      loading.value = true;
      userStore.restoreFromStorage();
      if (!userStore.isLoggedIn) {
        stats.value = { totalRecords: 0, totalDays: 0, currentStreak: 0, topDishes: [] };
        history.value = [];
        loading.value = false;
        return;
      }
      try {
        const openid = userStore.openid;
        await utils_login.refreshUserInfo();
        const [statsData, records] = await Promise.all([
          api_records.fetchStats(openid),
          api_records.fetchRecords(openid)
        ]);
        stats.value = statsData;
        history.value = records.slice(0, 3);
      } catch (e) {
        stats.value = { totalRecords: 0, totalDays: 0, currentStreak: 0, topDishes: [] };
        history.value = [];
        utils_toast.toastError(e, "加载失败，请稍后重试");
      } finally {
        loading.value = false;
      }
    }
    common_vendor.onShow(() => {
      loadData();
    });
    async function handleIdentityCard() {
      if (userStore.isLoggedIn) {
        goSettings();
        return;
      }
      router.push({ name: "login" });
    }
    function goReport() {
      router.push({ name: "report" });
    }
    function goGallery() {
      router.push({ name: "gallery" });
    }
    function goSettings() {
      router.push({ name: "settings" });
    }
    async function updateAvatar(filePath) {
      if (!userStore.isLoggedIn) {
        utils_toast.toastError(null, "请先登录再修改头像");
        return;
      }
      if (!filePath || avatarUpdating.value)
        return;
      avatarUpdating.value = true;
      try {
        const uploaded = await api_request.uploadFile(filePath, "avatar");
        await api_auth.updateUserInfo({ openid: userStore.openid, avatarUrl: uploaded.url });
        await utils_login.refreshUserInfo();
        utils_toast.toastSuccess("头像已更新");
      } catch (e) {
        utils_toast.toastError(e, "头像更新失败，请重试");
      } finally {
        avatarUpdating.value = false;
      }
    }
    function onChooseAvatar() {
      if (!userStore.isLoggedIn) {
        utils_toast.toastError(null, "请先登录再修改头像");
        return;
      }
      if (avatarUpdating.value) {
        utils_toast.toast("头像更新中，请稍候");
        return;
      }
      utils_chooseImage.chooseImageFile({
        onSelected: (filePath) => updateAvatar(filePath),
        onFail: () => utils_toast.toast("选择图片失败，请重试")
      });
    }
    function showPrivacy() {
      router.push({ name: "privacy" });
    }
    function goAbout() {
      router.push({ name: "about" });
    }
    function goTimeline() {
      router.push({ name: "timeline" });
    }
    function openHistoryRecord(item) {
      common_vendor.index.setStorageSync("mrc_timeline_record_id", item.id);
      goTimeline();
    }
    function goPreferences() {
      router.push({ name: "preferences" });
    }
    function goWeeklyPlan() {
      router.push({ name: "meal-agent" });
    }
    function goMembership() {
      router.push({ name: "membership" });
    }
    function goFeedback() {
      router.push({ name: "feedback" });
    }
    function openStat(type) {
      if (!userStore.isLoggedIn) {
        router.push({ name: "login" });
        return;
      }
      try {
        common_vendor.index.vibrateShort({ type: "light" });
      } catch {
      }
      router.push({ name: type === "records" ? "timeline" : "calendar" });
    }
    return (_ctx, _cache) => {
      var _a, _b, _c, _d;
      return common_vendor.e({
        a: common_vendor.p({
          name: "settings",
          size: "24px",
          color: "#EF5A3C"
        }),
        b: common_vendor.o(goSettings),
        c: `${common_vendor.unref(nav).statusBarHeight}px`,
        d: `${common_vendor.unref(nav).navBarHeight}px`,
        e: `${common_vendor.unref(nav).capsuleRightGap}px`,
        f: common_vendor.unref(userStore).isLoggedIn ? `${common_vendor.unref(utils_assets.STATIC_BASE_URL)}/static/guozai/action_08_peek.png` : `${common_vendor.unref(utils_assets.STATIC_BASE_URL)}/static/guozai/action_09_celebrate.png`,
        g: common_vendor.unref(userStore).isLoggedIn ? ((_a = common_vendor.unref(userStore).userInfo) == null ? void 0 : _a.avatarUrl) || `${common_vendor.unref(utils_assets.STATIC_BASE_URL)}/static/guozai/mood_01_happy.png` : `${common_vendor.unref(utils_assets.STATIC_BASE_URL)}/static/guozai/action_09_celebrate.png`,
        h: ((_b = common_vendor.unref(userStore).userInfo) == null ? void 0 : _b.avatarUrl) ? "aspectFill" : "aspectFit",
        i: common_vendor.unref(userStore).isLoggedIn
      }, common_vendor.unref(userStore).isLoggedIn ? {
        j: common_vendor.p({
          name: "settings",
          size: "14px",
          color: "#EF5A3C"
        })
      } : {}, {
        k: avatarUpdating.value
      }, avatarUpdating.value ? {} : {}, {
        l: ((_c = common_vendor.unref(userStore).userInfo) == null ? void 0 : _c.avatarUrl) ? 1 : "",
        m: common_vendor.unref(userStore).isLoggedIn ? "更换头像" : "登录后即可更换头像",
        n: common_vendor.o(onChooseAvatar),
        o: common_vendor.t(common_vendor.unref(userStore).isLoggedIn ? "锅仔的小饭友" : "锅仔在这里等你"),
        p: common_vendor.t(common_vendor.unref(userStore).isLoggedIn ? ((_d = common_vendor.unref(userStore).userInfo) == null ? void 0 : _d.nickname) || "给自己取个昵称" : "微信登录"),
        q: common_vendor.t(common_vendor.unref(userStore).isLoggedIn ? "点击进入设置，修改头像和昵称" : "登录后让锅仔慢慢记住你的口味"),
        r: common_vendor.unref(userStore).isLoggedIn ? 1 : "",
        s: common_vendor.t(common_vendor.unref(userStore).isLoggedIn ? "微信身份已连接" : "点击即可微信登录"),
        t: common_vendor.unref(userStore).isLoggedIn ? "打开设置修改个人资料" : "微信登录",
        v: common_vendor.o(handleIdentityCard),
        w: common_vendor.t(stats.value.totalRecords),
        x: common_vendor.o(($event) => openStat("records")),
        y: common_vendor.t(stats.value.totalDays),
        z: common_vendor.o(($event) => openStat("days")),
        A: common_vendor.t(stats.value.currentStreak),
        B: common_vendor.o(($event) => openStat("streak")),
        C: common_vendor.t(activeMembership.value ? "锅仔会员陪伴中" : "开通锅仔会员"),
        D: common_vendor.t(activeMembership.value ? "已开通" : "看权益"),
        E: common_vendor.o(goMembership),
        F: `${common_vendor.unref(utils_assets.STATIC_BASE_URL)}/static/guozai/action_10_thinking.png`,
        G: common_vendor.o(goPreferences),
        H: `${common_vendor.unref(utils_assets.STATIC_BASE_URL)}/static/guozai/action_06_glasses.png`,
        I: common_vendor.o(goWeeklyPlan),
        J: `${common_vendor.unref(utils_assets.STATIC_BASE_URL)}/static/guozai/mood_06_hungry.png`,
        K: common_vendor.o(goGallery),
        L: `${common_vendor.unref(utils_assets.STATIC_BASE_URL)}/static/guozai/action_09_celebrate.png`,
        M: common_vendor.o(goReport),
        N: `${common_vendor.unref(utils_assets.STATIC_BASE_URL)}/static/guozai/action_06_glasses.png`,
        O: common_vendor.o(goTimeline),
        P: common_vendor.t(stats.value.totalRecords),
        Q: common_vendor.o(goTimeline),
        R: history.value.length === 0
      }, history.value.length === 0 ? {
        S: `${common_vendor.unref(utils_assets.STATIC_BASE_URL)}/static/guozai/action_08_peek.png`
      } : {}, {
        T: common_vendor.f(history.value, (item, i, i0) => {
          var _a2;
          return common_vendor.e({
            a: item.dishName,
            b: item.imageUrl,
            c: common_vendor.t((_a2 = item.recordDate) == null ? void 0 : _a2.slice(5)),
            d: common_vendor.t(item.dishName),
            e: `${item.moodTag}心情`,
            f: MOOD_IMG_MAP[item.moodTag] || `${common_vendor.unref(utils_assets.STATIC_BASE_URL)}/static/guozai/mood_01_happy.png`,
            g: common_vendor.t(item.moodTag),
            h: common_vendor.t(item.cookingTime || 30),
            i: item.note
          }, item.note ? {
            j: common_vendor.t(item.note)
          } : {}, {
            k: item.id,
            l: `查看 ${item.recordDate} 的${item.dishName}记录`,
            m: i === 0 ? 1 : "",
            n: common_vendor.o(($event) => openHistoryRecord(item), item.id)
          });
        }),
        U: history.value.length
      }, history.value.length ? {
        V: `${common_vendor.unref(utils_assets.STATIC_BASE_URL)}/static/guozai/action_08_peek.png`
      } : {}, {
        W: common_vendor.o(showPrivacy),
        X: common_vendor.o(goFeedback),
        Y: common_vendor.o(goAbout),
        Z: `${common_vendor.unref(utils_assets.STATIC_BASE_URL)}/static/guozai/mood_01_happy.png`
      });
    };
  }
});
const MiniProgramPage = /* @__PURE__ */ common_vendor._export_sfc(_sfc_main, [["__scopeId", "data-v-f97f9319"]]);
exports.MiniProgramPage = MiniProgramPage;
