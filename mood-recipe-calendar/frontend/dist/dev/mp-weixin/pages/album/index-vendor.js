"use strict";
const common_vendor = require("../../common/vendor.js");
const composables_useNavBar = require("../../composables/useNavBar.js");
const utils_assets = require("../../utils/assets.js");
const api_albums = require("../../api/albums.js");
const api_records = require("../../api/records.js");
const api_virtualCommerce = require("../../api/virtualCommerce.js");
const stores_user = require("../../stores/user.js");
const utils_albumLayout = require("../../utils/albumLayout.js");
const utils_albumShare = require("../../utils/albumShare.js");
const utils_login = require("../../utils/login.js");
const utils_toast = require("../../utils/toast.js");
const __unplugin_components_0 = () => "../../node-modules/@wot-ui/ui/components/wd-navbar/wd-navbar.js";
if (!Array) {
  const _component_wd_navbar = __unplugin_components_0;
  const _component_layout_default_uni = common_vendor.resolveComponent("layout-default-uni");
  (_component_wd_navbar + _component_layout_default_uni)();
}
if (!Math) {
  (Icon + LoadingState + ErrorState)();
}
const Icon = () => "../../components/common/Icon.js";
const ErrorState = () => "../../components/guozai/ErrorState.js";
const LoadingState = () => "../../components/guozai/LoadingState.js";
const _sfc_main = /* @__PURE__ */ common_vendor.defineComponent({
  __name: "index",
  setup(__props) {
    const currentPage = common_vendor.ref(0);
    const loading = common_vendor.ref(true);
    const error = common_vendor.ref("");
    const album = common_vendor.ref(null);
    const records = common_vendor.ref([]);
    const userStore = stores_user.useUserStore();
    const now = /* @__PURE__ */ new Date();
    const monthStr = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, "0")}`;
    const monthNum = now.getMonth() + 1;
    const yearNum = now.getFullYear();
    const EMPTY_STATS = { totalDays: 0, moodDistribution: {}, topDishes: [], longestStreak: 0 };
    const stats = common_vendor.computed(() => {
      var _a;
      if (!((_a = album.value) == null ? void 0 : _a.stats))
        return EMPTY_STATS;
      try {
        const parsed = JSON.parse(album.value.stats);
        return {
          totalDays: Number(parsed.totalDays) || 0,
          moodDistribution: parsed.moodDistribution || {},
          topDishes: Array.isArray(parsed.topDishes) ? parsed.topDishes : [],
          longestStreak: Number(parsed.longestStreak) || 0
        };
      } catch {
        return EMPTY_STATS;
      }
    });
    const moodList = common_vendor.computed(() => {
      const dist = stats.value.moodDistribution || {};
      return Object.entries(dist).map(([mood, count]) => ({ mood, count })).sort((a, b) => b.count - a.count);
    });
    const topDishes = common_vendor.computed(() => (stats.value.topDishes || []).slice(0, 3));
    const ownerName = common_vendor.computed(() => {
      var _a, _b;
      return ((_b = (_a = userStore.userInfo) == null ? void 0 : _a.nickname) == null ? void 0 : _b.trim()) || "我的";
    });
    const recordCount = common_vendor.computed(() => records.value.length);
    const monthlyInsight = common_vendor.computed(() => {
      var _a, _b;
      if (!recordCount.value)
        return "第一顿记录，会成为锅仔认识你的开始。";
      const mood = (_a = moodList.value[0]) == null ? void 0 : _a.mood;
      const dish = (_b = topDishes.value[0]) == null ? void 0 : _b.name;
      if (mood && dish)
        return `你常在「${mood}」时留下饭香，也最常做「${dish}」。`;
      return `你留下了 ${recordCount.value} 顿真实的饭，锅仔都收好了。`;
    });
    const personalFacts = common_vendor.computed(() => [
      moodList.value[0] ? `最常记录 · ${moodList.value[0].mood} ${moodList.value[0].count}次` : "",
      topDishes.value[0] ? `熟悉味道 · ${topDishes.value[0].name}` : ""
    ].filter(Boolean));
    const recordPages = common_vendor.computed(() => utils_albumLayout.chunkPages(records.value));
    const DAILY_AREA = { x: 0, y: 0, w: 678, h: 690 };
    const dailyBoxes = common_vendor.computed(
      () => recordPages.value.map((pg) => utils_albumLayout.autoLayout(pg.length, DAILY_AREA, 22))
    );
    const totalPages = common_vendor.computed(() => 3 + recordPages.value.length);
    async function loadAlbum() {
      loading.value = true;
      error.value = "";
      try {
        const openid = await utils_login.ensureLogin();
        const [albumData, recordData] = await Promise.all([
          api_albums.fetchMonthAlbum(openid, monthStr),
          api_records.fetchRecordsByMonth(openid, monthStr),
          utils_login.refreshUserInfo(true)
        ]);
        album.value = albumData;
        records.value = recordData;
        await refreshEntitlements(openid);
      } catch (e) {
        error.value = e.message || "加载失败";
      } finally {
        loading.value = false;
      }
    }
    common_vendor.onShow(() => {
      currentPage.value = 0;
      loadAlbum();
    });
    function onSwiperChange(e) {
      currentPage.value = e.detail.current;
    }
    function next() {
      if (currentPage.value < totalPages.value - 1)
        currentPage.value++;
    }
    function prev() {
      if (currentPage.value > 0)
        currentPage.value--;
    }
    const sharing = common_vendor.ref(false);
    const purchasing = common_vendor.ref(false);
    const nav = composables_useNavBar.useNavBar();
    const shareTop = common_vendor.computed(() => `${nav.statusBarHeight + nav.navBarHeight + 8}px`);
    const entitlementRemaining = common_vendor.ref(null);
    const isMember = common_vendor.computed(() => {
      var _a, _b;
      return ((_a = userStore.userInfo) == null ? void 0 : _a.isMember) === 1 && Boolean((_b = userStore.userInfo) == null ? void 0 : _b.memberExpire) && new Date(userStore.userInfo.memberExpire).getTime() > Date.now();
    });
    const hasAlbumEntitlement = common_vendor.computed(() => isMember.value || entitlementRemaining.value !== null && entitlementRemaining.value > 0);
    const shareButtonText = common_vendor.computed(() => hasAlbumEntitlement.value ? `保存我的${monthNum}月干饭分享图` : "解锁并保存高清分享图");
    async function refreshEntitlements(openid) {
      const list = await api_virtualCommerce.fetchEntitlements();
      const item = list.find((e) => e.code === api_virtualCommerce.ALBUM_ENTITLEMENT_CODE);
      entitlementRemaining.value = (item == null ? void 0 : item.remainingUses) ?? null;
    }
    async function onShare() {
      var _a;
      if (sharing.value || purchasing.value)
        return;
      let openid = "";
      try {
        openid = await utils_login.ensureLogin();
      } catch (e) {
        utils_toast.toastError(e, "请先登录");
        return;
      }
      if (!hasAlbumEntitlement.value) {
        const purchased = await purchaseAlbum(openid);
        if (!purchased)
          return;
      }
      sharing.value = true;
      common_vendor.index.showLoading({ title: "正在生成分享图..." });
      try {
        const topMood = moodList.value[0];
        await utils_albumShare.exportAlbumShare({
          brand: "心情菜谱日历",
          title: `${yearNum}年${monthNum}月干饭日记`,
          totalDays: stats.value.totalDays || 0,
          topDish: ((_a = topDishes.value[0]) == null ? void 0 : _a.name) || "—",
          topMood: topMood ? `${topMood.mood} ${topMood.count}天` : "—",
          slogan: "用一道菜，治愈今天的你",
          guozaiPath: `${utils_assets.STATIC_BASE_URL}/static/guozai/mood_01_happy.png`,
          footer: "「锅仔」· 你的情绪味蕾搭子",
          aiAssisted: true
        });
        if (!isMember.value && hasAlbumEntitlement.value) {
          try {
            const result = await api_virtualCommerce.consumeEntitlement(openid, api_virtualCommerce.ALBUM_ENTITLEMENT_CODE);
            entitlementRemaining.value = result.remainingUses;
          } catch {
            utils_toast.toast("高清图已保存，但权益扣减异常，请稍后刷新");
          }
        }
        utils_toast.toastSuccess("已保存到相册");
      } catch (e) {
        utils_toast.toastError(e, "导出失败");
      } finally {
        common_vendor.index.hideLoading();
        sharing.value = false;
      }
    }
    async function purchaseAlbum(openid) {
      if (purchasing.value)
        return false;
      purchasing.value = true;
      let checkingDelivery = false;
      try {
        const order = await api_virtualCommerce.createVirtualOrder(openid, api_virtualCommerce.ALBUM_PRODUCT_SKU);
        const params = await api_virtualCommerce.getVirtualPaymentParams(openid, order.orderNo);
        await api_virtualCommerce.requestWechatVirtualPayment(params);
        checkingDelivery = true;
        common_vendor.index.showLoading({ title: "锅仔正在确认权益…", mask: true });
        const delivered = await waitForDelivery(order.orderNo);
        if (!delivered) {
          utils_toast.toast("支付已完成，权益确认中，稍后刷新即可保存");
          return false;
        }
        await refreshEntitlements(openid);
        return hasAlbumEntitlement.value;
      } catch (e) {
        utils_toast.toastError(e, "暂时无法发起支付");
        return false;
      } finally {
        if (checkingDelivery)
          common_vendor.index.hideLoading();
        purchasing.value = false;
      }
    }
    function waitForDelivery(orderNo) {
      return (async () => {
        for (let attempt = 0; attempt < 4; attempt += 1) {
          await new Promise((resolve) => setTimeout(resolve, attempt === 0 ? 900 : 1600));
          if ((await api_virtualCommerce.fetchVirtualOrder(orderNo)).status === "DELIVERED")
            return true;
        }
        return false;
      })();
    }
    function moodSegStyle(mood, count) {
      const total = moodList.value.reduce((s, m) => s + m.count, 0) || 1;
      return { width: `${Math.max(count / total * 100, 8)}%`, background: utils_albumLayout.MOOD_COLOR[mood] || "#E8836B" };
    }
    function dailyCellStyle(box) {
      return { left: `${box.x}rpx`, top: `${box.y}rpx`, width: `${box.w}rpx`, height: `${box.h}rpx` };
    }
    function dailyTagSize(name) {
      return name && name.length > 5 ? "20rpx" : "24rpx";
    }
    function recordDateLabel(date) {
      return (date == null ? void 0 : date.slice(5).replace("-", ".")) || "本月";
    }
    const aiLines = common_vendor.computed(() => {
      var _a;
      const t = (_a = album.value) == null ? void 0 : _a.aiSummary;
      if (!t)
        return ["这个月，你好好吃饭了。", "下个月，请继续对自己好一点。"];
      return t.split("\n").filter(Boolean);
    });
    return (_ctx, _cache) => {
      var _a;
      return common_vendor.e({
        a: common_vendor.o(common_vendor.unref(composables_useNavBar.navBack)),
        b: common_vendor.p({
          title: "月度画册",
          ["left-arrow"]: true,
          ["safe-area-inset-top"]: true,
          ["custom-style"]: "background-color: transparent !important;"
        }),
        c: common_vendor.p({
          name: "share",
          size: 36,
          color: "#6A4A37"
        }),
        d: !hasAlbumEntitlement.value
      }, !hasAlbumEntitlement.value ? {} : {}, {
        e: shareTop.value,
        f: common_vendor.o(onShare),
        g: loading.value
      }, loading.value ? {
        h: common_vendor.p({
          text: "锅仔正在装订画册..."
        })
      } : error.value ? {
        j: common_vendor.o(loadAlbum)
      } : common_vendor.e({
        k: common_vendor.t(ownerName.value),
        l: common_vendor.t(monthNum),
        m: common_vendor.t(common_vendor.unref(yearNum)),
        n: common_vendor.t(monthNum),
        o: common_vendor.t(stats.value.totalDays),
        p: `${common_vendor.unref(utils_assets.STATIC_BASE_URL)}/static/guozai/action_05_album.png`,
        q: common_vendor.t(monthNum),
        r: common_vendor.t(common_vendor.unref(yearNum)),
        s: common_vendor.t(String(monthNum).padStart(2, "0")),
        t: common_vendor.t(stats.value.totalDays || 0),
        v: common_vendor.t(recordCount.value),
        w: common_vendor.t(stats.value.longestStreak || 0),
        x: `${common_vendor.unref(utils_assets.STATIC_BASE_URL)}/static/guozai/mood_10_content.png`,
        y: moodList.value.length
      }, moodList.value.length ? {
        z: common_vendor.f(moodList.value.slice(0, 3), (m, k0, i0) => {
          return {
            a: common_vendor.t(m.mood),
            b: common_vendor.t(m.count),
            c: common_vendor.s(moodSegStyle(m.mood, m.count)),
            d: m.mood
          };
        })
      } : {}, {
        A: topDishes.value.length
      }, topDishes.value.length ? {
        B: common_vendor.f(topDishes.value, (dish, i, i0) => {
          return {
            a: common_vendor.t(i + 1),
            b: common_vendor.t(dish.name),
            c: common_vendor.t(dish.count),
            d: dish.name
          };
        })
      } : {}, {
        C: common_vendor.t(monthlyInsight.value),
        D: common_vendor.f(recordPages.value, (pg, pi, i0) => {
          return {
            a: common_vendor.t(String(pi + 1).padStart(2, "0")),
            b: common_vendor.f(pg, (rec, idx, i1) => {
              return common_vendor.e({
                a: rec.imageUrl
              }, rec.imageUrl ? {
                b: rec.imageUrl
              } : {
                c: common_vendor.t(common_vendor.unref(utils_albumLayout.MOOD_EMOJI)[rec.moodTag] || "🍽️")
              }, {
                d: common_vendor.t(recordDateLabel(rec.recordDate)),
                e: common_vendor.t(rec.moodTag || "一顿饭"),
                f: common_vendor.t(rec.dishName),
                g: dailyTagSize(rec.dishName),
                h: rec.id,
                i: common_vendor.s(dailyCellStyle(dailyBoxes.value[pi][idx]))
              });
            }),
            c: `daily-${pi}`
          };
        }),
        E: common_vendor.t(String(recordPages.value.length).padStart(2, "0")),
        F: `${common_vendor.unref(utils_assets.STATIC_BASE_URL)}/static/guozai/mood_01_happy.png`,
        G: !recordPages.value.length
      }, !recordPages.value.length ? {
        H: `${common_vendor.unref(utils_assets.STATIC_BASE_URL)}/static/guozai/action_07_empty.png`
      } : {}, {
        I: common_vendor.t(ownerName.value),
        J: common_vendor.t(monthNum),
        K: common_vendor.f(personalFacts.value, (fact, k0, i0) => {
          return {
            a: common_vendor.t(fact),
            b: fact
          };
        }),
        L: common_vendor.f(aiLines.value, (line, i, i0) => {
          return {
            a: common_vendor.t(line),
            b: i
          };
        }),
        M: `${common_vendor.unref(utils_assets.STATIC_BASE_URL)}/static/guozai/action_06_glasses.png`,
        N: `${common_vendor.unref(utils_assets.STATIC_BASE_URL)}/static/guozai/mood_01_happy.png`,
        O: common_vendor.t(stats.value.totalDays || 0),
        P: common_vendor.t(((_a = topDishes.value[0]) == null ? void 0 : _a.name) || "—"),
        Q: common_vendor.t(moodList.value[0] ? `${moodList.value[0].mood}最多` : "—"),
        R: `${common_vendor.unref(utils_assets.STATIC_BASE_URL)}/static/guozai/action_09_celebrate.png`,
        S: common_vendor.t(shareButtonText.value),
        T: common_vendor.o(onShare),
        U: hasAlbumEntitlement.value
      }, hasAlbumEntitlement.value ? {
        V: common_vendor.t(entitlementRemaining.value)
      } : {}, {
        W: currentPage.value,
        X: common_vendor.o(onSwiperChange),
        Y: currentPage.value > 0
      }, currentPage.value > 0 ? {
        Z: common_vendor.o(prev)
      } : {}, {
        aa: common_vendor.f(totalPages.value, (i, k0, i0) => {
          return {
            a: i,
            b: currentPage.value === i - 1 ? 1 : ""
          };
        }),
        ab: currentPage.value < totalPages.value - 1
      }, currentPage.value < totalPages.value - 1 ? {
        ac: common_vendor.o(next)
      } : {
        ad: common_vendor.o(onShare)
      }), {
        i: error.value
      });
    };
  }
});
const MiniProgramPage = /* @__PURE__ */ common_vendor._export_sfc(_sfc_main, [["__scopeId", "data-v-884a3ac5"]]);
exports.MiniProgramPage = MiniProgramPage;
