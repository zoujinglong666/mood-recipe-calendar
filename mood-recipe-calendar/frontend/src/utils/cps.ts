import { toast, toastSuccess } from './toast'

/**
 * 电商导购（CPS）跳转封装。
 *
 * [2026-10-28 更正] 个人主体小程序可以跳转第三方小程序：wx.navigateToMiniProgram
 * 只要拿到目标 appid + path 即可用（需用户点击触发，跳转前有系统确认弹窗）。
 * 之前注释里「个人主体不允许跳转」是误区，真正的个人主体限制是 web-view 组件不可用。
 *
 * 当前没跳的真正原因：还没有填 PDD_APP_ID / PDD_DEFAULT_PATH（拼多多推广位信息）。
 * 填上即生效，采购清单页无需改动。审核风险在类目/内容，不在跳转能力本身。
 */

/** 拼多多主小程序 appId（预留，填入即生效——个人主体可直接跳转第三方小程序） */
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
