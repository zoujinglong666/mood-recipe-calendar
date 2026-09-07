import { login as apiLogin } from '../api/auth'
import { useUserStore } from '../stores/user'

/**
 * 将登录失败错误转换为可操作的提示。
 * 重点识别「invalid code」：通常由 appid 不匹配 / 游客模式导致，
 * 此时 wx.login 返回的 code 无法被后端 code2Session 兑换。
 */
function normalizeLoginError(e: unknown): string {
  const msg = e instanceof Error ? e.message : String(e)
  if (/invalid code/i.test(msg)) {
    return '微信登录失败：code 无效（invalid code）。请确认：\n1) 微信开发者工具已用真实微信账号登录（非游客模式）；\n2) manifest.json 的 mp-weixin.appid 已填写为真实小程序 appid；\n3) 后端 code2Session 使用的 appid / secret 与该 appid 一致。'
  }
  return msg || '微信登录失败'
}

/**
 * 确保用户已登录，返回 openid。
 * 正式环境仅支持微信小程序：uni.login 获取真实 code → 后端 code2session 换 openid。
 * H5 仅用于本地开发预览，固定走 mock（h5_dev_ 前缀），不触发微信 code2session。
 */
export async function ensureLogin(): Promise<string> {
  const userStore = useUserStore()

  // 已登录直接返回
  if (userStore.isLoggedIn) {
    return userStore.openid
  }

  // 从本地存储恢复
  userStore.restoreFromStorage()
  if (userStore.isLoggedIn) {
    return userStore.openid
  }

  // #ifdef H5
  // H5 仅本地预览：固定 mock code（h5_dev_ 前缀），后端走 mock，不请求微信
  const mockCode = 'h5_dev_' + Math.random().toString(36).slice(2, 10)
  const h5Result = await apiLogin(mockCode)
  userStore.setLogin(h5Result.openid, h5Result.sessionToken, h5Result.user)
  return h5Result.openid
  // #endif

  // #ifndef H5
  // 微信小程序正式登录：wx.login 获取 code
  return new Promise((resolve, reject) => {
    uni.login({
      provider: 'weixin',
      success: async (res) => {
        if (res.code) {
          try {
            const result = await apiLogin(res.code)
            userStore.setLogin(result.openid, result.sessionToken, result.user)
            resolve(result.openid)
          } catch (e) {
            reject(new Error(normalizeLoginError(e)))
          }
        } else {
          reject(new Error('微信登录失败：未获取到 code'))
        }
      },
      fail: () => reject(new Error('微信登录失败')),
    })
  })
  // #endif
}

/**
 * 刷新当前用户信息（登录后从后端拉取最新资料）
 */
export async function refreshUserInfo(): Promise<void> {
  const userStore = useUserStore()
  if (!userStore.openid) return
  const { getUserInfo } = await import('../api/auth')
  try {
    const info = await getUserInfo(userStore.openid)
    userStore.setLogin(userStore.openid, userStore.sessionToken, info)
  } catch {
    // 静默失败，保留本地缓存
  }
}
