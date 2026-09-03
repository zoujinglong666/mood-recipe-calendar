import { login as apiLogin } from '../api/auth'
import { useUserStore } from '../stores/user'

/**
 * 确保用户已登录，返回 openid。
 * 正式环境（微信小程序）：wx.login 获取 code → 后端 code2session 换 openid。
 * H5（仅本地开发调试）：mock 固定 code，保证数据连贯。
 */
export async function ensureLogin(): Promise<string> {
  const userStore = useUserStore()

  // 已登录直接返回
  if (userStore.openid) {
    return userStore.openid
  }

  // 从本地存储恢复
  userStore.restoreFromStorage()
  if (userStore.openid) {
    return userStore.openid
  }

  // #ifdef H5
  // H5 仅用于本地开发调试：固定 mock code
  const result = await apiLogin('h5_dev_user', '小圆', '')
  userStore.setLogin(result.openid, result.user)
  return result.openid
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
            userStore.setLogin(result.openid, result.user)
            resolve(result.openid)
          } catch (e) {
            reject(e)
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
    userStore.setLogin(userStore.openid, info)
  } catch {
    // 静默失败，保留本地缓存
  }
}
