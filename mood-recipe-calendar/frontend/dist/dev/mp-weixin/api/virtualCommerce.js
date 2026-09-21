"use strict";
const common_vendor = require("../common/vendor.js");
const api_request = require("./request.js");
const ALBUM_PRODUCT_SKU = "ALBUM_HD_EXPORT";
const ALBUM_ENTITLEMENT_CODE = "ALBUM_HD_EXPORT";
const MEMBER_PRODUCT_SKU = "GUOZAI_MEMBER_30D";
function fetchVirtualProducts() {
  return api_request.get("/virtual-commerce/products");
}
function createVirtualOrder(openid, sku) {
  return api_request.post("/virtual-commerce/orders", { sku });
}
function getVirtualPaymentParams(openid, orderNo) {
  return api_request.post(`/virtual-commerce/orders/${orderNo}/payment-params`);
}
function fetchVirtualOrder(orderNo) {
  return api_request.get(`/virtual-commerce/orders/${orderNo}`);
}
function requestWechatVirtualPayment(params) {
  return new Promise((resolve, reject) => {
    const requestVirtualPayment = common_vendor.index.requestVirtualPayment;
    if (typeof requestVirtualPayment !== "function") {
      reject(new Error("当前微信版本暂不支持虚拟支付"));
      return;
    }
    requestVirtualPayment({
      mode: params.mode,
      signData: params.signData,
      paySig: params.paySig,
      signature: params.signature,
      success: () => resolve(),
      fail: (error) => reject(error)
    });
  });
}
function fetchEntitlements(_openid) {
  return api_request.get("/virtual-commerce/entitlements");
}
function consumeEntitlement(_openid, code) {
  return api_request.post("/virtual-commerce/entitlements/consume", { code });
}
exports.ALBUM_ENTITLEMENT_CODE = ALBUM_ENTITLEMENT_CODE;
exports.ALBUM_PRODUCT_SKU = ALBUM_PRODUCT_SKU;
exports.MEMBER_PRODUCT_SKU = MEMBER_PRODUCT_SKU;
exports.consumeEntitlement = consumeEntitlement;
exports.createVirtualOrder = createVirtualOrder;
exports.fetchEntitlements = fetchEntitlements;
exports.fetchVirtualOrder = fetchVirtualOrder;
exports.fetchVirtualProducts = fetchVirtualProducts;
exports.getVirtualPaymentParams = getVirtualPaymentParams;
exports.requestWechatVirtualPayment = requestWechatVirtualPayment;
