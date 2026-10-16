import { toast, toastSuccess } from './toast'

/**
 * 电商导购（CPS）跳转封装——当前为占位版。
 *
 * 当前小程序是个人主体，微信不允许 wx.navigateToMiniProgram 跳第三方小程序，
 * 所以这里只做复制清单 + 引导去 App 搜索。
 *
 * 未来升级企业/个体户主体后，把下面 PDD_APP_ID / PDD_DEFAULT_PATH 填上，
 * 并放开 #ifdef MP-WEIXIN 里的跳转块即可，采购清单页无需改动。
 */

/** 拼多多主小程序 appId（预留，升级主体后填入即生效） */
const PDD_APP_ID = ''

/**
 * 默认推广 path（预留）：从多多进宝「单商品推广 → 小程序路径」复制，
 * 自带 pid=15526082_318641574。用户进来领券后 15 天内下单计佣金。
 */
const PDD_DEFAULT_PATH = ''

/** 复制文本到剪贴板（H5/小程序统一封装） */
function copyText(text: string): Promise<void> {
  return new Promise((resolve, reject) => {
    uni.setClipboardData({
      data: text,
      showToast: false,
      success: () => resolve(),
      fail: () => reject(new Error('复制失败')),
    })
  })
}

/**
 * 一键买齐：复制采购清单，引导用户去拼多多/超市搜索。
 * 未来接入 CPS 后会先尝试跳拼多多小程序，失败再降级复制。
 * @param keyword 食材关键词（预留，未用）
 * @param fallbackCopy 要复制到剪贴板的清单文本
 */
export async function goToMeituanBuy(keyword: string, fallbackCopy: string): Promise<void> {
  // #ifdef MP-WEIXIN
  if (PDD_APP_ID && PDD_DEFAULT_PATH) {
    try {
      await new Promise<void>((resolve, reject) => {
        uni.navigateToMiniProgram({
          appId: PDD_APP_ID,
          path: PDD_DEFAULT_PATH,
          success: () => resolve(),
          fail: err => reject(new Error(err?.errMsg || '跳转失败')),
        })
      })
      return
    } catch {
      // 跳转失败 → 落到复制清单兜底
    }
  }
  // #endif
  try {
    await copyText(fallbackCopy)
    toastSuccess('清单已复制，打开拼多多/超市搜索即可')
  } catch {
    toast('复制失败，请手动记录')
  }
}
