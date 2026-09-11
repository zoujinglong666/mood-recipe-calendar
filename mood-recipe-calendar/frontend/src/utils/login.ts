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
 * 仅支持微信小程序：uni.login 获取真实 code → 后端 code2session 换 openid。
 */
export async function ensureLogin(): Promise<string> {
  const userStore = useUserStore()

  // 已登录直接返回
  if (userStore.isLoggedIn) {
    return userStore.openid
  }

  // 用户主动退出登录后，页面 onShow 自动调用时不应静默重新登录
  if (userStore.userInitiatedLogout) {
    throw new Error('NOT_LOGGED_IN')
  }

  // 从本地存储恢复
  userStore.restoreFromStorage()
  if (userStore.isLoggedIn) {
    return userStore.openid
  }

  // 微信小程序登录：wx.login 获取 code
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
}

/**
 * 刷新当前用户信息（登录后从后端拉取最新资料）
 * 带5分钟缓存，避免频繁调用 /api/auth/user
 * @param force 是否强制刷新（忽略缓存）
 */
let lastUserInfoRefresh = 0
const USER_INFO_CACHE_MS = 5 * 60 * 1000 // 5分钟

export async function refreshUserInfo(force = false): Promise<void> {
  const userStore = useUserStore()
  if (!userStore.openid) return
  // 缓存检查：5分钟内不重复调用，除非强制刷新
  if (!force && Date.now() - lastUserInfoRefresh < USER_INFO_CACHE_MS)
    return
  const { getUserInfo } = await import('../api/auth')
  try {
    const info = await getUserInfo(userStore.openid)
    userStore.setLogin(userStore.openid, userStore.sessionToken, info)
    lastUserInfoRefresh = Date.now()
  } catch {
    // 静默失败，保留本地缓存
  }
}
