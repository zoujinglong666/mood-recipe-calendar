import { ref } from 'vue'
import {
  createVirtualOrder,
  fetchVirtualOrder,
  getVirtualPaymentParams,
  requestWechatVirtualPayment,
} from '@/api/virtualCommerce'
import { refreshWechatLoginForPayment } from '@/utils/login'
import { toast, toastError } from '@/utils/toast'

/**
 * 虚拟商品购买流程：下单 → 取签名 → 微信虚拟支付 → 轮询发货。
 * 与 recipe 页私人菜单购买同链路，抽成 composable 供专辑页复用。
 */
export function useVirtualPurchase() {
  const purchasingSku = ref('')

  async function purchase(sku: string, onDelivered?: () => void) {
    if (purchasingSku.value)
      return
    purchasingSku.value = sku
    let checkingDelivery = false
    try {
      const openid = await refreshWechatLoginForPayment()
      const order = await createVirtualOrder(openid, sku)
      const params = await getVirtualPaymentParams(openid, order.orderNo)
      await requestWechatVirtualPayment(params)
      checkingDelivery = true
      uni.showLoading({ title: '锅仔正在确认权益…', mask: true })
      const delivered = await waitForDelivery(order.orderNo)
      onDelivered?.()
      toast(delivered ? '权益已到账，去看看吧' : '支付已完成，权益确认中')
    }
    catch (e: any) {
      toastError(e, '暂时无法发起支付')
    }
    finally {
      if (checkingDelivery)
        uni.hideLoading()
      purchasingSku.value = ''
    }
  }

  async function waitForDelivery(orderNo: string) {
    for (let attempt = 0; attempt < 4; attempt += 1) {
      await new Promise(resolve => setTimeout(resolve, attempt === 0 ? 900 : 1600))
      if ((await fetchVirtualOrder(orderNo)).status === 'DELIVERED')
        return true
    }
    return false
  }

  return { purchasingSku, purchase }
}
