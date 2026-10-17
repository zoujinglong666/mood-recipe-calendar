"use strict";
const common_vendor = require("../../common/vendor.js");
const api_records = require("../../api/records.js");
const composables_useNavBar = require("../../composables/useNavBar.js");
const utils_assets = require("../../utils/assets.js");
const stores_user = require("../../stores/user.js");
const utils_login = require("../../utils/login.js");
const utils_toast = require("../../utils/toast.js");
const composables_useWishlist = require("../../composables/useWishlist.js");
const api_share = require("../../api/share.js");
const api_gallery = require("../../api/gallery.js");
const api_fridge = require("../../api/fridge.js");
const api_request = require("../../api/request.js");
const api_auth = require("../../api/auth.js");
const utils_chooseImage = require("../../utils/chooseImage.js");
const __unplugin_components_1 = () => "../../node-modules/@wot-ui/ui/components/wd-popup/wd-popup.js";
const __unplugin_components_0 = () => "../../node-modules/@wot-ui/ui/components/wd-icon/wd-icon.js";
if (!Array) {
  const _component_wd_icon = __unplugin_components_0;
  const _component_wd_popup = __unplugin_components_1;
  const _component_layout_tabbar_uni = common_vendor.resolveComponent("layout-tabbar-uni");
  (_component_wd_icon + _component_wd_popup + _component_layout_tabbar_uni)();
}
if (!Math) {
  SuccessModal();
}
const SuccessModal = () => "../../components/guozai/SuccessModal.js";
const _sfc_defineComponent = common_vendor.defineComponent({
  __name: "index",
  setup(__props) {
    const router = common_vendor.useRouter();
    const userStore = stores_user.useUserStore();
    const { favorites, syncFromServer: syncWishlist } = composables_useWishlist.useWishlist();
    common_vendor.onShareAppMessage(() => {
      const sharer = userStore.openid;
      const path = `/pages/profile/index${sharer ? `?sharer=${encodeURIComponent(sharer)}` : ""}`;
      return { title: "锅仔 · 按心情帮你决定今天吃什么", path };
    });
    common_vendor.onShareTimeline(() => {
      const sharer = userStore.openid;
      return {
        title: "锅仔 · 按心情帮你决定今天吃什么",
        query: sharer ? `sharer=${encodeURIComponent(sharer)}` : ""
      };
    });
    const nav = composables_useNavBar.useNavBar();
    const loading = common_vendor.ref(true);
    const avatarUpdating = common_vendor.ref(false);
    const showEdit = common_vendor.ref(false);
    const editNickname = common_vendor.ref("");
    const nicknameSaving = common_vendor.ref(false);
    const stats = common_vendor.ref({ totalRecords: 0, totalDays: 0, currentStreak: 0, topDishes: [] });
    const history = common_vendor.ref([]);
    const shareStatus = common_vendor.ref({ isSharer: false, sharedToday: false });
    const checkinStatus = common_vendor.ref(null);
    const checkingIn = common_vendor.ref(false);
    const fridgeSummary = common_vendor.ref();
    const fridgeSummaryText = common_vendor.computed(() => {
      const s = fridgeSummary.value;
      if (!s)
        return "记下食材，锅仔帮你看着";
      if (s.expired > 0)
        return `有 ${s.expired} 项已过期，请先处理`;
      if (s.soon > 0)
        return `今天优先消耗 ${s.soon} 项食材`;
      return `已记录 ${s.total} 项食材`;
    });
    const fridgeHasAlert = common_vendor.computed(() => {
      const s = fridgeSummary.value;
      return !!s && (s.expired > 0 || s.soon > 0);
    });
    const showCheckinModal = common_vendor.ref(false);
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
        await utils_login.refreshUserInfo();
        history.value = (await api_records.fetchRecords()).slice(0, 3);
        api_records.fetchStats().then((s) => {
          stats.value = s;
        }).catch(() => {
        });
        api_share.fetchShareStatus().then((s) => {
          shareStatus.value = s;
        }).catch(() => {
        });
        api_gallery.fetchCheckinStatus().then((s) => {
          checkinStatus.value = s;
        }).catch(() => {
        });
        api_fridge.fetchFridgeSummary().then((s) => {
          fridgeSummary.value = s;
        }).catch(() => {
        });
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
      syncWishlist();
      achievementUnread.value = Number(common_vendor.index.getStorageSync("mrc_achv_unread") || 0);
    });
    async function handleIdentityCard() {
      if (userStore.isLoggedIn) {
        goSettings();
        return;
      }
      utils_login.navigateToLogin();
    }
    function goReport() {
      router.push({ name: "report" });
    }
    const achievementUnread = common_vendor.ref(0);
    function goGallery() {
      router.push({ name: "gallery" });
    }
    function goSettings() {
      router.push({ name: "settings" });
    }
    async function updateAvatar(filePath) {
      if (!userStore.isLoggedIn) {
        utils_login.navigateToLogin();
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
        utils_login.navigateToLogin();
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
    function openEdit() {
      var _a;
      if (!userStore.isLoggedIn) {
        utils_login.navigateToLogin();
        return;
      }
      editNickname.value = ((_a = userStore.userInfo) == null ? void 0 : _a.nickname) || "";
      showEdit.value = true;
    }
    function onChooseWechatAvatar(event) {
      var _a;
      updateAvatar(((_a = event.detail) == null ? void 0 : _a.avatarUrl) || "");
    }
    async function saveEditNickname() {
      var _a, _b;
      const value = editNickname.value.trim();
      if (!userStore.isLoggedIn) {
        utils_toast.toast("请先登录再修改昵称");
        return;
      }
      if (!value) {
        utils_toast.toast("昵称不能为空");
        return;
      }
      if (nicknameSaving.value || value === ((_a = userStore.userInfo) == null ? void 0 : _a.nickname))
        return;
      nicknameSaving.value = true;
      try {
        await api_auth.updateUserInfo({ openid: userStore.openid, nickname: value });
        await utils_login.refreshUserInfo(true);
        editNickname.value = ((_b = userStore.userInfo) == null ? void 0 : _b.nickname) || value;
        utils_toast.toastSuccess("昵称已保存");
      } catch (e) {
        utils_toast.toastError(e, "昵称保存失败，请重试");
      } finally {
        nicknameSaving.value = false;
      }
    }
    function showPrivacy() {
      router.push({ name: "privacy" });
    }
    function goAbout() {
      router.push({ name: "about" });
    }
    let lastTimelineNavAt = 0;
    function goTimeline() {
      const now = Date.now();
      if (now - lastTimelineNavAt < 600)
        return;
      lastTimelineNavAt = now;
      router.push({ name: "timeline" }).catch(() => {
        router.replace({ name: "timeline" }).catch(() => {
          utils_toast.toast("暂时打不开时光机，请稍后再试");
          lastTimelineNavAt = 0;
        });
      });
    }
    function goRecommendationHistory() {
      router.push({ name: "recommendation-history" });
    }
    function goWishlist() {
      router.push({ name: "wishlist" });
    }
    let lastHistoryNavAt = 0;
    function openHistoryRecord(item) {
      const now = Date.now();
      if (now - lastHistoryNavAt < 600)
        return;
      lastHistoryNavAt = now;
      const params = { id: String(item.id) };
      router.push({ name: "record-detail", params }).catch(() => {
        router.replace({ name: "record-detail", params }).catch(() => {
          utils_toast.toast("这条记录暂时打不开，请稍后再试");
          lastHistoryNavAt = 0;
        });
      });
    }
    function goPreferences() {
      router.push({ name: "preferences" });
    }
    function goFridge() {
      router.push({ name: "fridge" });
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
    async function quickCheckin() {
      var _a;
      if (!userStore.isLoggedIn) {
        utils_login.navigateToLogin();
        return;
      }
      if (checkingIn.value || ((_a = checkinStatus.value) == null ? void 0 : _a.checkedIn))
        return;
      checkingIn.value = true;
      try {
        await api_gallery.doCheckin();
        checkinStatus.value = await api_gallery.fetchCheckinStatus();
        showCheckinModal.value = true;
      } catch (e) {
        utils_toast.toastError(e, "签到失败，请稍后再试");
      } finally {
        checkingIn.value = false;
      }
    }
    function openStat(type) {
      if (!userStore.isLoggedIn) {
        utils_login.navigateToLogin();
        return;
      }
      try {
        common_vendor.index.vibrateShort({ type: "light" });
      } catch {
      }
      router.push({ name: type === "records" ? "timeline" : "calendar" });
    }
    return (_ctx, _cache) => {
      var _a, _b, _c, _d, _e, _f, _g, _h, _i, _j, _k, _l;
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
        m: common_vendor.unref(userStore).isLoggedIn ? "修改头像与昵称" : "登录后即可修改资料",
        n: common_vendor.o(openEdit),
        o: common_vendor.t(common_vendor.unref(userStore).isLoggedIn ? "锅仔的小饭友" : "锅仔在这里等你"),
        p: common_vendor.t(common_vendor.unref(userStore).isLoggedIn ? ((_d = common_vendor.unref(userStore).userInfo) == null ? void 0 : _d.nickname) || "给自己取个昵称" : "微信登录"),
        q: common_vendor.unref(userStore).isLoggedIn ? "修改昵称" : "登录后即可修改昵称",
        r: common_vendor.o(openEdit),
        s: common_vendor.t(common_vendor.unref(userStore).isLoggedIn ? "点击进入设置，修改头像和昵称" : "登录后让锅仔慢慢记住你的口味"),
        t: common_vendor.unref(userStore).isLoggedIn ? 1 : "",
        v: common_vendor.t(common_vendor.unref(userStore).isLoggedIn ? "微信身份已连接" : "点击即可微信登录"),
        w: common_vendor.unref(userStore).isLoggedIn ? "打开设置修改个人资料" : "微信登录",
        x: common_vendor.o(handleIdentityCard),
        y: common_vendor.t(stats.value.totalRecords),
        z: common_vendor.o(($event) => openStat("records")),
        A: common_vendor.t(stats.value.totalDays),
        B: common_vendor.o(($event) => openStat("days")),
        C: common_vendor.t(stats.value.currentStreak),
        D: common_vendor.o(($event) => openStat("streak")),
        E: `${common_vendor.unref(utils_assets.STATIC_BASE_URL)}/static/guozai/action_06_glasses.png`,
        F: common_vendor.t(activeMembership.value ? "锅仔会员陪伴中" : "开通锅仔会员"),
        G: common_vendor.t(activeMembership.value ? "已开通" : "看权益"),
        H: common_vendor.o(goMembership),
        I: `${common_vendor.unref(utils_assets.STATIC_BASE_URL)}/static/guozai/action_07_empty.png`,
        J: common_vendor.t(fridgeSummaryText.value),
        K: fridgeHasAlert.value ? 1 : "",
        L: common_vendor.o(goFridge),
        M: `${common_vendor.unref(utils_assets.STATIC_BASE_URL)}/static/guozai/action_10_thinking.png`,
        N: common_vendor.o(goPreferences),
        O: `${common_vendor.unref(utils_assets.STATIC_BASE_URL)}/static/guozai/action_06_glasses.png`,
        P: common_vendor.o(goWeeklyPlan),
        Q: `${common_vendor.unref(utils_assets.STATIC_BASE_URL)}/static/guozai/mood_06_hungry.png`,
        R: common_vendor.t(achievementUnread.value > 0 ? `有 ${achievementUnread.value} 个新成就，去点亮` : "成就徽章 · 表情包 · 我的订单"),
        S: achievementUnread.value > 0
      }, achievementUnread.value > 0 ? {
        T: common_vendor.t(achievementUnread.value)
      } : {}, {
        U: shareStatus.value.isSharer
      }, shareStatus.value.isSharer ? {} : {}, {
        V: common_vendor.o(goGallery),
        W: common_vendor.t(((_e = checkinStatus.value) == null ? void 0 : _e.checkedIn) ? "今日已签到" : "每日签到 · 领 3 轮对话"),
        X: common_vendor.t(((_f = checkinStatus.value) == null ? void 0 : _f.checkedIn) ? `已连续 ${checkinStatus.value.streak} 天，明天再来` : "签到后今天可和锅仔聊 3 轮"),
        Y: common_vendor.t(((_g = checkinStatus.value) == null ? void 0 : _g.checkedIn) ? "已签" : checkingIn.value ? "签到中" : "签到"),
        Z: ((_h = checkinStatus.value) == null ? void 0 : _h.checkedIn) ? 1 : "",
        aa: ((_i = checkinStatus.value) == null ? void 0 : _i.checkedIn) ? "今日已签到" : "签到领取今日锅仔对话",
        ab: common_vendor.o(quickCheckin),
        ac: common_vendor.o(($event) => showCheckinModal.value = false),
        ad: common_vendor.p({
          visible: showCheckinModal.value,
          title: "签到成功！",
          subtitle: `今日锅仔对话 +3 轮，已连续签到 ${((_j = checkinStatus.value) == null ? void 0 : _j.streak) ?? 1} 天`,
          ["confirm-text"]: "好的"
        }),
        ae: `${common_vendor.unref(utils_assets.STATIC_BASE_URL)}/static/guozai/action_09_celebrate.png`,
        af: common_vendor.o(goReport),
        ag: `${common_vendor.unref(utils_assets.STATIC_BASE_URL)}/static/guozai/action_06_glasses.png`,
        ah: common_vendor.o(goTimeline),
        ai: `${common_vendor.unref(utils_assets.STATIC_BASE_URL)}/static/guozai/action_08_peek.png`,
        aj: common_vendor.t(common_vendor.unref(favorites).length ? `已收藏 ${common_vendor.unref(favorites).length} 道` : "以后想做的菜"),
        ak: common_vendor.o(goWishlist),
        al: `${common_vendor.unref(utils_assets.STATIC_BASE_URL)}/static/guozai/action_02_soup.png`,
        am: common_vendor.o(goRecommendationHistory),
        an: common_vendor.t(stats.value.totalRecords),
        ao: common_vendor.o(goTimeline),
        ap: history.value.length === 0
      }, history.value.length === 0 ? {
        aq: `${common_vendor.unref(utils_assets.STATIC_BASE_URL)}/static/guozai/action_08_peek.png`
      } : {}, {
        ar: common_vendor.f(history.value, (item, i, i0) => {
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
        as: history.value.length
      }, history.value.length ? {
        at: `${common_vendor.unref(utils_assets.STATIC_BASE_URL)}/static/guozai/action_08_peek.png`
      } : {}, {
        av: common_vendor.o(showPrivacy),
        aw: common_vendor.o(goFeedback),
        ax: common_vendor.o(goAbout),
        ay: `${common_vendor.unref(utils_assets.STATIC_BASE_URL)}/static/guozai/mood_01_happy.png`,
        az: common_vendor.p({
          name: "close",
          size: "22px",
          color: "#A1826A"
        }),
        aA: common_vendor.o(($event) => showEdit.value = false),
        aB: ((_k = common_vendor.unref(userStore).userInfo) == null ? void 0 : _k.avatarUrl) || `${common_vendor.unref(utils_assets.STATIC_BASE_URL)}/static/guozai/mood_01_happy.png`,
        aC: avatarUpdating.value
      }, avatarUpdating.value ? {} : {}, {
        aD: avatarUpdating.value,
        aE: common_vendor.o(onChooseWechatAvatar),
        aF: common_vendor.o(onChooseAvatar),
        aG: common_vendor.o(saveEditNickname),
        aH: editNickname.value,
        aI: common_vendor.o(($event) => editNickname.value = $event.detail.value),
        aJ: common_vendor.t(nicknameSaving.value ? "保存中…" : "保存昵称"),
        aK: nicknameSaving.value || !editNickname.value.trim() || editNickname.value.trim() === ((_l = common_vendor.unref(userStore).userInfo) == null ? void 0 : _l.nickname) ? 1 : "",
        aL: common_vendor.o(saveEditNickname),
        aM: common_vendor.o(($event) => showEdit.value = $event),
        aN: common_vendor.p({
          position: "bottom",
          ["z-index"]: 30,
          ["close-on-click-modal"]: true,
          ["safe-area-inset-bottom"]: true,
          ["custom-style"]: "border-radius: 40rpx 40rpx 0 0; overflow: hidden; background: var(--mrc-surface);",
          modelValue: showEdit.value
        })
      });
    };
  }
});
_sfc_defineComponent.__runtimeHooks = 6;
const MiniProgramPage = /* @__PURE__ */ common_vendor._export_sfc(_sfc_defineComponent, [["__scopeId", "data-v-f97f9319"]]);
exports.MiniProgramPage = MiniProgramPage;
