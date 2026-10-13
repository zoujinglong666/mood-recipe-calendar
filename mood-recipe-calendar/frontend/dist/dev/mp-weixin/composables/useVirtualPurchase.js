"use strict";
const common_vendor = require("../common/vendor.js");
const api_virtualCommerce = require("../api/virtualCommerce.js");
const utils_login = require("../utils/login.js");
const utils_toast = require("../utils/toast.js");
function useVirtualPurchase() {
  const purchasingSku = common_vendor.ref("");
  async function purchase(sku, onDelivered) {
    if (purchasingSku.value)
      return;
    purchasingSku.value = sku;
    let checkingDelivery = false;
    try {
      const openid = await utils_login.refreshWechatLoginForPayment();
      const order = await api_virtualCommerce.createVirtualOrder(openid, sku);
      const params = await api_virtualCommerce.getVirtualPaymentParams(openid, order.orderNo);
      await api_virtualCommerce.requestWechatVirtualPayment(params);
      checkingDelivery = true;
      common_vendor.index.showLoading({ title: "锅仔正在确认权益…", mask: true });
      const delivered = await waitForDelivery(order.orderNo);
      onDelivered == null ? void 0 : onDelivered();
      utils_toast.toast(delivered ? "权益已到账，去看看吧" : "支付已完成，权益确认中");
    } catch (e) {
      utils_toast.toastError(e, "暂时无法发起支付");
    } finally {
      if (checkingDelivery)
        common_vendor.index.hideLoading();
      purchasingSku.value = "";
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
  return { purchasingSku, purchase };
}
exports.useVirtualPurchase = useVirtualPurchase;
