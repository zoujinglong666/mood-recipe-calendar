import { get, post } from './request'

/** 周边商品 */
export interface Product {
  id: number
  name: string
  image: string
  price: number
  exchangePrice: number
  category: string
  description?: string
  stock: number
}

/** 签到状态 */
export interface CheckinStatus {
  checkedIn: boolean
  streak: number
  exchangeReady: boolean
  daysToExchange: number
  totalDays: number
}

/** 订单 */
export interface ShopOrder {
  id: number
  orderNo: string
  openid: string
  productId: number
  productName: string
  productImage?: string
  amount: number
  payType: 'normal' | 'exchange'
  status: 'pending' | 'paid' | 'cancelled'
  createdAt?: string
  paidAt?: string
}

/** 周边商品列表 */
export function fetchProducts() {
  return get<Product[]>('/gallery/products')
}

/** 今日签到 */
export function doCheckin(openid: string) {
  return post<CheckinStatus>('/gallery/checkin', { openid })
}

/** 签到状态 */
export function fetchCheckinStatus(openid: string) {
  return get<CheckinStatus>('/gallery/checkin/status', { openid })
}

/** 创建订单（payType: normal 原价 / exchange 1元兑换） */
export function createOrder(openid: string, productId: number, payType: 'normal' | 'exchange') {
  return post<ShopOrder>('/gallery/orders', { openid, productId, payType })
}

/** 确认支付成功（联调入口；真实环境由微信虚拟支付回调驱动） */
export function payOrder(orderId: number) {
  return post<{ orderId: number; status: string; amount: number }>(`/gallery/orders/${orderId}/pay`)
}

/** 我的订单 */
export function fetchOrders(openid: string) {
  return get<ShopOrder[]>('/gallery/orders', { openid })
}
