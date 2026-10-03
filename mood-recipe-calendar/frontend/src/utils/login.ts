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
 *
 * 并发去重：服务端每次登录都会覆盖 sessionTokenHash（单 token 模型），
 * 若并发（如多张图片同时上传触发）各发起一次登录，后一次会让前一次的 token 立即失效，
 * 表现为「只有最后一个请求成功」。这里复用同一个 in-flight Promise，保证只登录一次。
 */
let loginInFlight: Promise<string> | null = null

function startWechatLogin(): Promise<string> {
  if (loginInFlight)
    return loginInFlight

  loginInFlight = new Promise<string>((resolve, reject) => {
    uni.login({
      provider: 'weixin',
      success: async (res) => {
        if (res.code) {
          try {
            const result = await apiLogin(res.code)
            useUserStore().setLogin(result.openid, result.sessionToken, result.user)
            resolve(result.openid)
          }
          catch (e) {
            reject(new Error(normalizeLoginError(e)))
          }
        }
        else {
          reject(new Error('微信登录失败：未获取到 code'))
        }
      },
      fail: () => reject(new Error('微信登录失败')),
    })
  }).finally(() => { loginInFlight = null })

  return loginInFlight
}

export function ensureLogin(): Promise<string> {
  const userStore = useUserStore()

  // 已登录直接返回
  if (userStore.isLoggedIn) {
    return Promise.resolve(userStore.openid)
  }

  // 用户主动退出登录后，页面 onShow 自动调用时不应静默重新登录
  if (userStore.userInitiatedLogout) {
    return Promise.reject(new Error('NOT_LOGGED_IN'))
  }

  // 从本地存储恢复
  userStore.restoreFromStorage()
  if (userStore.isLoggedIn) {
    return Promise.resolve(userStore.openid)
  }

  return startWechatLogin()
}

/**
 * 虚拟支付必须使用当前设备最新的微信 session_key 生成用户态签名。
 * 不能复用普通会话的 sessionToken：同一账号在另一设备登录后，旧 session_key
 * 仍能访问业务接口，却会在 requestVirtualPayment 时被微信拒绝为 SIGNATURE_INVALID。
 */
export function refreshWechatLoginForPayment(): Promise<string> {
  const userStore = useUserStore()
  if (userStore.userInitiatedLogout)
    return Promise.reject(new Error('NOT_LOGGED_IN'))
  return startWechatLogin()
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

/**
 * 跳转到登录页，并对「未登录用户反复点击多个需登录入口」做去重：
 * 同一时刻只会产生一个 login 页面实例，避免把 login 页在导航栈里叠加 20 层
 * （对应 We 分析里「login 页 1 人打开 20 次」的体验问题）。
 * 已处于 login 页或正在跳转中则直接忽略本次调用。
 */
let loginNavigating = false
export function navigateToLogin(): void {
  const pages = getCurrentPages() as Array<{ route?: string }>
  const top = pages[pages.length - 1]
  if (top && typeof top.route === 'string' && top.route.endsWith('/login'))
    return
  if (loginNavigating)
    return
  loginNavigating = true
  // uni.navigateTo 在运行时返回 Promise，但当前类型声明为 void，这里做安全断言
  ;(uni.navigateTo({ url: '/pages/login/index' }) as unknown as Promise<void>)
    .finally(() => { loginNavigating = false })
}
