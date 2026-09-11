const RETURN_KEY = 'mrc_auth_return'
const LOGIN_ROUTE = 'pages/login/index'
let recovering = false

function currentInternalPath() {
  const pages = typeof getCurrentPages === 'function' ? getCurrentPages() : []
  const page = pages[pages.length - 1] as any
  const route = String(page?.route || '')
  if (!/^(?:pages|subPages)\//.test(route) || route === LOGIN_ROUTE)
    return ''
  const query = Object.entries(page?.options || {})
    .map(([key, value]) => `${encodeURIComponent(key)}=${encodeURIComponent(String(value))}`)
    .join('&')
  return `/${route}${query ? `?${query}` : ''}`
}

/** 首个 401 保存回跳目标；同一轮并发请求不会再次触发导航。 */
export function beginAuthRecovery() {
  if (recovering)
    return false
  recovering = true
  const target = currentInternalPath()
  if (target)
    uni.setStorageSync(RETURN_KEY, target)
  uni.$emit('auth:expired')
  return true
}

export function consumeAuthReturn() {
  const target = String(uni.getStorageSync(RETURN_KEY) || '')
  uni.removeStorageSync(RETURN_KEY)
  recovering = false
  return /^(?:\/pages|\/subPages)\//.test(target) ? target : ''
}

export function cancelAuthRecovery() {
  uni.removeStorageSync(RETURN_KEY)
  recovering = false
}
