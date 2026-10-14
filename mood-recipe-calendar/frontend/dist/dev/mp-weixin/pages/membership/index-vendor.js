"use strict";
const common_vendor = require("../../common/vendor.js");
const composables_useNavBar = require("../../composables/useNavBar.js");
const utils_assets = require("../../utils/assets.js");
const api_virtualCommerce = require("../../api/virtualCommerce.js");
const api_family = require("../../api/family.js");
const stores_user = require("../../stores/user.js");
const utils_login = require("../../utils/login.js");
const utils_toast = require("../../utils/toast.js");
if (!Array) {
  const _component_layout_default_uni = common_vendor.resolveComponent("layout-default-uni");
  _component_layout_default_uni();
}
const MEMBER_NOTICE_TEMPLATE_ID = "03DBh5APFeME7iZl7akNURNuNvDCxW2e1GMMQiifNiY";
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
    const router = common_vendor.useRouter();
    const nav = composables_useNavBar.useNavBar();
    const userStore = stores_user.useUserStore();
    const loading = common_vendor.ref(true);
    const paying = common_vendor.ref("");
    function requestMemberNoticeAuthorization() {
      return new Promise((resolve) => {
        const requestSubscribeMessage = common_vendor.index.requestSubscribeMessage;
        if (typeof requestSubscribeMessage !== "function") {
          resolve(false);
          return;
        }
        requestSubscribeMessage({
          tmplIds: [MEMBER_NOTICE_TEMPLATE_ID],
          success: (res) => resolve(["accept", "acceptWithAudio"].includes(res[MEMBER_NOTICE_TEMPLATE_ID])),
          fail: () => resolve(false)
        });
      });
    }
    async function enableRenewalNotice() {
      const accepted = await requestMemberNoticeAuthorization();
      if (accepted)
        utils_toast.toastSuccess("已开启，会员到期与续费成功后会通知你");
      else
        utils_toast.toast("未开启授权，不影响会员使用");
    }
    const product = common_vendor.ref(null);
    const yearProduct = common_vendor.ref(null);
    const error = common_vendor.ref("");
    const family = common_vendor.ref(null);
    const familyLoaded = common_vendor.ref(false);
    const joinCode = common_vendor.ref("");
    const familyBusy = common_vendor.ref(false);
    let paymentSupported = false;
    paymentSupported = typeof common_vendor.index.requestVirtualPayment === "function";
    const FALLBACK_GROUPS = [
      {
        key: "MEAL",
        eyebrow: "MEAL",
        title: "每天吃什么，锅仔替你想",
        items: [
          { icon: "✦", title: "锅仔智能体不限次对话", value: "免费用户仅签到送 3 轮，会员不限次", detail: "想换菜、想调整、想问怎么做，随时开口；结合人数、预算、忌口和不做饭的日期。" },
          { icon: "▤", title: "周菜单不限次生成与重排", value: "免费用户每周 1 次，会员不限次", detail: "安排整周晚餐、一键生成购物清单；哪天不合适，直接让锅仔换一道。" },
          { icon: "600", title: "首页推荐每天 20 次", value: "免费用户每天 3 次", detail: "按你的心情、口味和忌口推荐，量足到可以任性挑；额度每天刷新。" },
          { icon: "◇", title: "AI 私人菜单深度定制", value: "一次给到贴合你的整份菜单", detail: "输入心情和现有食材，生成一份完整可用、带做法的私人菜单，不用逐项凑。" }
        ]
      },
      {
        key: "FRIDGE",
        eyebrow: "FRIDGE",
        title: "冰箱里有什么，拍一张就知道",
        items: [
          { icon: "◉", title: "冰箱拍照识别食材", value: "拍一张，自动认食材、算保质期", detail: "对着冰箱拍一张，锅仔自动认出食材并估算保质期，确认后一键入库，不用一个个手输。" },
          { icon: "⏱", title: "食材临期提醒", value: "快过期时提醒你，别浪费", detail: "临期或过期的食材会主动提醒，并告诉你哪些其实不该放冰箱、该怎么存。" }
        ]
      },
      {
        key: "MEMORY",
        eyebrow: "MEMORY",
        title: "认真吃过的每一餐，都替你收好",
        items: [
          { icon: "▣", title: "高清无水印月度画册", value: "把这一个月认真收藏下来", detail: "将当月真实记录、心情和锅仔寄语整理成无水印收藏图，随时保存、分享。" },
          { icon: "↗", title: "锅仔联网搜索", value: "问到库外知识也能答", detail: "时令食材、食材挑选、某道菜的多样做法，锅仔可以联网帮你查，并注明来源。" }
        ]
      }
    ];
    const remoteBenefits = common_vendor.ref([]);
    const benefitGroups = common_vendor.computed(() => {
      const remote = remoteBenefits.value.filter((group) => {
        var _a;
        return (_a = group.items) == null ? void 0 : _a.length;
      }).map((group) => ({
        key: group.key,
        eyebrow: (group.key || "").toUpperCase(),
        title: group.title || "",
        items: group.items.map((item) => ({
          icon: item.icon || "·",
          title: item.title || "",
          value: item.value || "",
          detail: item.detail || ""
        }))
      }));
      return remote.length ? remote : FALLBACK_GROUPS;
    });
    const benefitCount = common_vendor.computed(() => benefitGroups.value.reduce((total, group) => total + group.items.length, 0));
    common_vendor.onMounted(async () => {
      try {
        const groups = await api_virtualCommerce.fetchMemberBenefits();
        if (Array.isArray(groups) && groups.length)
          remoteBenefits.value = groups;
      } catch {
      }
    });
    const active = common_vendor.computed(() => userStore.isActiveMember);
    const expireText = common_vendor.computed(() => {
      var _a, _b;
      return ((_b = (_a = userStore.userInfo) == null ? void 0 : _a.memberExpire) == null ? void 0 : _b.slice(0, 10)) || "";
    });
    const price = common_vendor.computed(() => {
      var _a;
      return ((((_a = product.value) == null ? void 0 : _a.priceFen) || 990) / 100).toFixed(2);
    });
    const yearPrice = common_vendor.computed(() => {
      var _a;
      return ((((_a = yearProduct.value) == null ? void 0 : _a.priceFen) || 3900) / 100).toFixed(2);
    });
    const yearDailyPrice = common_vendor.computed(() => {
      var _a;
      return ((((_a = yearProduct.value) == null ? void 0 : _a.priceFen) || 3900) / 100 / 365).toFixed(2);
    });
    const purchasable = common_vendor.computed(() => {
      var _a;
      return paymentSupported && Boolean((_a = product.value) == null ? void 0 : _a.paymentAvailable);
    });
    const yearPurchasable = common_vendor.computed(() => {
      var _a;
      return paymentSupported && Boolean((_a = yearProduct.value) == null ? void 0 : _a.paymentAvailable);
    });
    async function load() {
      loading.value = true;
      error.value = "";
      try {
        await utils_login.ensureLogin();
        const products = await api_virtualCommerce.fetchVirtualProducts();
        product.value = products.find((item) => item.sku === api_virtualCommerce.MEMBER_PRODUCT_SKU) || null;
        yearProduct.value = products.find((item) => item.sku === api_virtualCommerce.MEMBER_YEAR_SKU) || null;
        utils_login.refreshUserInfo(true).catch(() => {
        });
        loadFamily();
      } catch (e) {
        error.value = (e == null ? void 0 : e.message) === "NOT_LOGGED_IN" ? "" : (e == null ? void 0 : e.message) || "权益加载失败";
      } finally {
        loading.value = false;
      }
    }
    common_vendor.onShow(load);
    async function loadFamily() {
      try {
        family.value = await api_family.fetchFamily();
      } catch {
        family.value = null;
      } finally {
        familyLoaded.value = true;
      }
    }
    async function onCreateFamily() {
      if (familyBusy.value)
        return;
      familyBusy.value = true;
      try {
        family.value = await api_family.createFamily();
        utils_toast.toastSuccess("家庭已创建，把邀请码发给家人吧");
      } catch (e) {
        utils_toast.toastError(e, "创建家庭失败");
      } finally {
        familyBusy.value = false;
      }
    }
    async function onJoinFamily() {
      const code = joinCode.value.trim();
      if (!code) {
        utils_toast.toast("先输入家人的邀请码");
        return;
      }
      if (familyBusy.value)
        return;
      familyBusy.value = true;
      try {
        family.value = await api_family.joinFamily(code);
        joinCode.value = "";
        utils_toast.toastSuccess("已加入家庭");
      } catch (e) {
        utils_toast.toastError(e, "加入家庭失败");
      } finally {
        familyBusy.value = false;
      }
    }
    async function onLeaveFamily() {
      if (familyBusy.value)
        return;
      familyBusy.value = true;
      try {
        await api_family.leaveFamily();
        family.value = null;
        utils_toast.toast("已退出家庭");
      } catch (e) {
        utils_toast.toastError(e, "退出家庭失败");
      } finally {
        familyBusy.value = false;
      }
    }
    function copyInviteCode() {
      if (!family.value)
        return;
      common_vendor.index.setClipboardData({
        data: family.value.inviteCode,
        success: () => utils_toast.toast("邀请码已复制，发给家人")
      });
    }
    function goNutritionReport() {
      router.push({ name: "nutrition-report" });
    }
    async function purchase(sku) {
      const target = sku === api_virtualCommerce.MEMBER_YEAR_SKU ? yearProduct.value : product.value;
      if (paying.value)
        return;
      const isRenewal = active.value;
      if (!userStore.isLoggedIn) {
        utils_login.navigateToLogin();
        return;
      }
      const ok = sku === api_virtualCommerce.MEMBER_YEAR_SKU ? yearPurchasable.value : purchasable.value;
      if (!target || !ok) {
        utils_toast.toast(paymentSupported ? (target == null ? void 0 : target.paymentUnavailableMsg) || "会员暂不可开通" : "请在微信小程序内开通");
        return;
      }
      paying.value = sku;
      let checking = false;
      try {
        const openid = await utils_login.refreshWechatLoginForPayment();
        requestMemberNoticeAuthorization();
        const order = await api_virtualCommerce.createVirtualOrder(openid, target.sku);
        const params = await api_virtualCommerce.getVirtualPaymentParams(openid, order.orderNo);
        await api_virtualCommerce.requestWechatVirtualPayment(params);
        checking = true;
        common_vendor.index.showLoading({ title: "正在确认会员…", mask: true });
        const delivered = await waitForDelivery(order.orderNo);
        if (!delivered) {
          utils_toast.toast("支付已完成，会员正在到账，请稍后刷新");
          return;
        }
        await utils_login.refreshUserInfo(true);
        loadFamily();
        utils_toast.toastSuccess(isRenewal ? "续费成功，有效期已顺延" : sku === api_virtualCommerce.MEMBER_YEAR_SKU ? "年卡已开通，全家都能用了" : "锅仔会员已开通");
      } catch (e) {
        utils_toast.toastError(e, isRenewal ? "暂时无法续费" : "暂时无法开通会员");
      } finally {
        if (checking)
          common_vendor.index.hideLoading();
        paying.value = "";
      }
    }
    async function waitForDelivery(orderNo) {
      for (let attempt = 0; attempt < 4; attempt += 1) {
        await new Promise((resolve) => setTimeout(resolve, attempt === 0 ? 900 : 1600));
        if ((await api_virtualCommerce.fetchVirtualOrder(orderNo)).status === "DELIVERED")
          return true;
      }
      return false;
    }
    return (_ctx, _cache) => {
      return common_vendor.e({
        a: common_vendor.o(
          //@ts-ignore
          (...args) => common_vendor.unref(composables_useNavBar.navBack) && common_vendor.unref(composables_useNavBar.navBack)(...args)
        ),
        b: `${common_vendor.unref(nav).statusBarHeight}px`,
        c: `${common_vendor.unref(nav).navBarHeight}px`,
        d: common_vendor.t(active.value ? `会员中 · ${expireText.value} 到期` : "尚未开通"),
        e: active.value ? 1 : "",
        f: `${common_vendor.unref(utils_assets.STATIC_BASE_URL)}/static/guozai/action_06_glasses.png`,
        g: common_vendor.t(benefitCount.value),
        h: common_vendor.f(benefitGroups.value, (group, k0, i0) => {
          return {
            a: common_vendor.t(group.eyebrow),
            b: common_vendor.t(group.title),
            c: common_vendor.f(group.items, (item, index, i1) => {
              return {
                a: common_vendor.t(item.icon),
                b: common_vendor.t(String(index + 1).padStart(2, "0")),
                c: common_vendor.t(item.title),
                d: common_vendor.t(item.value),
                e: common_vendor.t(item.detail),
                f: item.title
              };
            }),
            d: group.key
          };
        }),
        i: error.value
      }, error.value ? {
        j: common_vendor.t(error.value),
        k: common_vendor.o(load)
      } : {}, {
        l: familyLoaded.value
      }, familyLoaded.value ? common_vendor.e({
        m: !family.value
      }, !family.value ? {
        n: common_vendor.t(familyBusy.value ? "创建中…" : "创建家庭"),
        o: familyBusy.value ? 1 : "",
        p: common_vendor.o(onCreateFamily),
        q: joinCode.value,
        r: common_vendor.o(($event) => joinCode.value = $event.detail.value),
        s: familyBusy.value ? 1 : "",
        t: common_vendor.o(onJoinFamily)
      } : {
        v: common_vendor.t(family.value.inviteCode),
        w: common_vendor.o(copyInviteCode),
        x: common_vendor.t(family.value.myAccessActive ? family.value.myRole === "OWNER" ? "你的会员权益生效中" : "你正在使用家人的会员权益" : "家庭创建者暂未开通会员，权益未生效"),
        y: family.value.myAccessActive ? 1 : "",
        z: common_vendor.f(family.value.members, (m, k0, i0) => {
          return common_vendor.e({
            a: m.avatarUrl || `${common_vendor.unref(utils_assets.STATIC_BASE_URL)}/static/guozai/mood_01_happy.png`,
            b: common_vendor.t(m.nickname),
            c: m.role === "OWNER"
          }, m.role === "OWNER" ? {} : m.openid === common_vendor.unref(userStore).openid ? {} : {}, {
            d: m.openid === common_vendor.unref(userStore).openid,
            e: m.openid
          });
        }),
        A: common_vendor.t(family.value.myRole === "OWNER" ? "解散家庭" : "退出家庭"),
        B: common_vendor.o(onLeaveFamily)
      }) : {}, {
        C: active.value
      }, active.value ? {
        D: `${common_vendor.unref(utils_assets.STATIC_BASE_URL)}/static/guozai/action_02_soup.png`,
        E: common_vendor.o(goNutritionReport)
      } : {}, {
        F: common_vendor.t(price.value),
        G: active.value
      }, active.value ? {
        H: common_vendor.t(expireText.value),
        I: common_vendor.o(enableRenewalNotice)
      } : {}, {
        J: common_vendor.t(yearPrice.value),
        K: common_vendor.t(yearDailyPrice.value),
        L: !yearPurchasable.value ? 1 : "",
        M: active.value ? "续费会员年卡" : "开通会员年卡",
        N: common_vendor.o(($event) => purchase(common_vendor.unref(api_virtualCommerce.MEMBER_YEAR_SKU))),
        O: common_vendor.t(price.value),
        P: common_vendor.t(active.value ? "续费 · 在现有效期上顺延 30 天" : "先试试"),
        Q: !purchasable.value ? 1 : "",
        R: active.value ? "续费会员月卡" : "开通会员月卡",
        S: common_vendor.o(($event) => purchase(common_vendor.unref(api_virtualCommerce.MEMBER_PRODUCT_SKU))),
        T: paying.value
      }, paying.value ? {} : {});
    };
  }
});
_sfc_defineComponent.__runtimeHooks = 6;
const MiniProgramPage = /* @__PURE__ */ common_vendor._export_sfc(_sfc_defineComponent, [["__scopeId", "data-v-20b0732c"]]);
exports.MiniProgramPage = MiniProgramPage;
