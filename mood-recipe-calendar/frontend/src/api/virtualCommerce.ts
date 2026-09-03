import { get, post } from './request'

export interface VirtualProduct {
  sku: string
  title: string
  description: string
  priceFen: number
  entitlementCode: string
  entitlementAmount: number
  validDays: number
}

export interface VirtualOrder {
  orderNo: string
  sku: string
  amountFen: number
  status: 'PENDING' | 'PAID' | 'DELIVERED' | 'REFUNDED' | 'CANCELLED'
}

export interface UserEntitlement {
  code: string
  remainingUses: number | null
  expiresAt: string | null
  status: 'ACTIVE' | 'EXPIRED' | 'REVOKED'
}

export interface VirtualPaymentParams {
  mode: 'short_series_goods'
  signData: string
  paySig: string
  signature: string
}

/** 商品目录只包含数字权益；实物周边仍使用 gallery API。 */
export function fetchVirtualProducts() {
  return get<VirtualProduct[]>('/virtual-commerce/products')
}

/** 创建待支付订单；真实支付参数由微信虚拟支付适配器完成后接入。 */
export function createVirtualOrder(openid: string, sku: string) {
  return post<VirtualOrder>('/virtual-commerce/orders', { openid, sku })
}

/** 服务端生成三项签名；AppKey、session_key 和价格校验均不会下发到客户端。 */
export function getVirtualPaymentParams(openid: string, orderNo: string) {
  return post<VirtualPaymentParams>(`/virtual-commerce/orders/${orderNo}/payment-params`, { openid })
}

/** 微信支付完成回调仅表示客户端流程结束；权益以服务端发货通知为准。 */
export function requestWechatVirtualPayment(params: VirtualPaymentParams) {
  return new Promise<void>((resolve, reject) => {
    // #ifdef MP-WEIXIN
    const requestVirtualPayment = (uni as any).requestVirtualPayment
    if (typeof requestVirtualPayment !== 'function') {
      reject(new Error('当前微信版本暂不支持虚拟支付'))
      return
    }
    requestVirtualPayment({
      mode: params.mode,
      signData: params.signData,
      paySig: params.paySig,
      signature: params.signature,
      success: () => resolve(),
      fail: (error) => reject(error),
    })
    // #endif
    // #ifndef MP-WEIXIN
    reject(new Error('虚拟支付仅支持在微信小程序中使用'))
    // #endif
  })
}

export function fetchEntitlements(openid: string) {
  return get<UserEntitlement[]>('/virtual-commerce/entitlements', { openid })
}
