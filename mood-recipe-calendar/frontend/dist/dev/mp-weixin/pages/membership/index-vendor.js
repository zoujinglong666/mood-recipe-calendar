"use strict";
const common_vendor = require("../../common/vendor.js");
const composables_useNavBar = require("../../composables/useNavBar.js");
const utils_assets = require("../../utils/assets.js");
const api_virtualCommerce = require("../../api/virtualCommerce.js");
const stores_user = require("../../stores/user.js");
const utils_login = require("../../utils/login.js");
const utils_toast = require("../../utils/toast.js");
if (!Array) {
  const _component_layout_default_uni = common_vendor.resolveComponent("layout-default-uni");
  _component_layout_default_uni();
}
const _sfc_main = /* @__PURE__ */ common_vendor.defineComponent({
  __name: "index",
  setup(__props) {
    const router = common_vendor.useRouter();
    const nav = composables_useNavBar.useNavBar();
    const userStore = stores_user.useUserStore();
    const loading = common_vendor.ref(true);
    const paying = common_vendor.ref(false);
    const product = common_vendor.ref(null);
    const error = common_vendor.ref("");
    let paymentSupported = false;
    paymentSupported = typeof common_vendor.index.requestVirtualPayment === "function";
    const benefits = [
      { icon: "✦", title: "锅仔管饭智能体", value: "把一家人的吃饭问题交给锅仔", detail: "结合人数、预算、忌口和不做饭日期，安排周菜单、购物清单并随时重排。" },
      { icon: "∞", title: "AI 私人菜单", value: "30 天内不限次数定制", detail: "按家中现有食材、时间、口味和健康目标，生成更贴合当下的一餐。" },
      { icon: "▣", title: "月度画册收藏版", value: "高清导出不限次数", detail: "把当月真实记录、心情和锅仔寄语整理成无水印收藏图，随时保存分享。" },
      { icon: "⌁", title: "一张会员通行证", value: "不用再逐项购买权益", detail: "30 天内统一覆盖智能规划、私人菜单和高清画册，家庭需求变化时随时回来调整。" }
    ];
    const active = common_vendor.computed(() => {
      var _a, _b;
      return ((_a = userStore.userInfo) == null ? void 0 : _a.isMember) === 1 && Boolean((_b = userStore.userInfo) == null ? void 0 : _b.memberExpire) && new Date(userStore.userInfo.memberExpire).getTime() > Date.now();
    });
    const expireText = common_vendor.computed(() => {
      var _a, _b;
      return ((_b = (_a = userStore.userInfo) == null ? void 0 : _a.memberExpire) == null ? void 0 : _b.slice(0, 10)) || "";
    });
    const price = common_vendor.computed(() => {
      var _a;
      return ((((_a = product.value) == null ? void 0 : _a.priceFen) || 990) / 100).toFixed(2);
    });
    const purchasable = common_vendor.computed(() => {
      var _a;
      return paymentSupported && Boolean((_a = product.value) == null ? void 0 : _a.platformItemId);
    });
    const actionText = common_vendor.computed(() => {
      if (active.value)
        return `会员有效至 ${expireText.value}`;
      if (!userStore.isLoggedIn)
        return "登录后开通会员";
      if (!paymentSupported)
        return "请在微信小程序内开通";
      if (!purchasable.value)
        return "会员道具配置中";
      return `¥${price.value} · 开通 30 天`;
    });
    async function load() {
      loading.value = true;
      error.value = "";
      try {
        await utils_login.ensureLogin();
        const [, products] = await Promise.all([utils_login.refreshUserInfo(true), api_virtualCommerce.fetchVirtualProducts()]);
        product.value = products.find((item) => item.sku === api_virtualCommerce.MEMBER_PRODUCT_SKU) || null;
      } catch (e) {
        error.value = (e == null ? void 0 : e.message) === "NOT_LOGGED_IN" ? "" : (e == null ? void 0 : e.message) || "权益加载失败";
      } finally {
        loading.value = false;
      }
    }
    common_vendor.onShow(load);
    async function purchase() {
      if (active.value || paying.value)
        return;
      if (!userStore.isLoggedIn) {
        router.push({ name: "login" });
        return;
      }
      if (!product.value || !purchasable.value) {
        utils_toast.toast(paymentSupported ? "会员道具 ID 配置后即可开通" : "请在微信小程序内开通");
        return;
      }
      paying.value = true;
      let checking = false;
      try {
        const openid = await utils_login.ensureLogin();
        const order = await api_virtualCommerce.createVirtualOrder(openid, product.value.sku);
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
        utils_toast.toastSuccess("锅仔会员已开通");
      } catch (e) {
        utils_toast.toastError(e, "暂时无法开通会员");
      } finally {
        if (checking)
          common_vendor.index.hideLoading();
        paying.value = false;
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
        g: common_vendor.f(benefits, (item, index, i0) => {
          return {
            a: common_vendor.t(item.icon),
            b: common_vendor.t(index + 1),
            c: common_vendor.t(item.title),
            d: common_vendor.t(item.value),
            e: common_vendor.t(item.detail),
            f: item.title
          };
        }),
        h: error.value
      }, error.value ? {
        i: common_vendor.t(error.value),
        j: common_vendor.o(load)
      } : {}, {
        k: common_vendor.t(active.value ? "已包含全部权益" : `¥${price.value}`),
        l: common_vendor.t(active.value ? "续费后有效期顺延" : "30 天权益包 · 非自动续费"),
        m: common_vendor.t(paying.value ? "确认中…" : actionText.value),
        n: active.value || loading.value || paying.value || !purchasable.value ? 1 : "",
        o: actionText.value,
        p: common_vendor.o(purchase)
      });
    };
  }
});
const MiniProgramPage = /* @__PURE__ */ common_vendor._export_sfc(_sfc_main, [["__scopeId", "data-v-20b0732c"]]);
exports.MiniProgramPage = MiniProgramPage;
