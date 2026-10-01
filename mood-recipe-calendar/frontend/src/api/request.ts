import { beginAuthRecovery } from '@/utils/authRecovery'

/**
 * 统一请求封装
 * 后端统一返回 { code, message, data }
 */

/** 预置后端服务（可在设置页切换） */
export interface ApiBackend {
  name: string
  url: string
  env: string
  desc: string
}

/** 仅开发/联调构建可见：本机与局域网后端（生产构建不导出） */
const DEV_ONLY_BACKENDS: ApiBackend[] = [
  { name: '本地开发', url: 'http://localhost:8080/api', env: 'development', desc: 'localhost:8080' },
  { name: '局域网开发', url: 'http://10.40.173.23:8080/api', env: 'development', desc: '10.40.173.23:8080（本机局域网）' },
]

/** 生产构建仅暴露正式环境，隐藏本地/局域网切换入口，避免审核与真机误切 */
const PROD_BACKENDS: ApiBackend[] = [
  { name: '生产环境', url: 'https://moodrecipe.icu/api', env: 'production', desc: 'moodrecipe.icu' },
]

export const API_BACKENDS: ApiBackend[] = import.meta.env.PROD
  ? PROD_BACKENDS
  : [...DEV_ONLY_BACKENDS, ...PROD_BACKENDS]

const STORAGE_KEY = 'apiBaseUrl'

/** 生产环境忽略本机/局域网地址，避免历史残留导致线上请求失败 */
function isTrustedApiUrl(url: string): boolean {
  if (!url || !/^https?:\/\//.test(url))
    return false
  try {
    const host = new URL(url).hostname.toLowerCase()
    if (!import.meta.env.PROD)
      return true
    return !/^(10\.|192\.168\.|172\.(1[6-9]|2\d|3[01])\.|127\.|localhost|0\.0\.0\.0)$/.test(host)
      && !/^\d+\.\d+\.\d+\.\d+$/.test(host)
  }
  catch {
    return false
  }
}

/** 是否为设置页里的预设后端（预设项可信，无需过生产白名单） */
function isKnownBackend(url: string): boolean {
  return API_BACKENDS.some(b => b.url === url)
}

/**
 * 读取当前后端地址：优先用户手动选择，其次编译环境变量，最后默认生产。
 * 生产构建的 API_BACKENDS 只含「生产环境」；历史残留的本地/局域网地址
 * 会被 isTrustedApiUrl 白名单拦截，回退到默认生产地址。
 */
export function getApiBaseUrl(): string {
  try {
    const saved = uni.getStorageSync(STORAGE_KEY)
    if (saved && (isKnownBackend(String(saved)) || isTrustedApiUrl(String(saved))))
      return String(saved)
  }
  catch {}
  return import.meta.env.VITE_API_BASE_URL || 'https://moodrecipe.icu/api'
}

/** 设置后端地址（持久化到本地，切换后立即生效）。非法地址不写入并提示，避免静默回退到生产 */
export function setApiBaseUrl(url: string): boolean {
  if (!url || !/^https?:\/\//.test(url)) {
    uni.showToast({ title: '地址格式不正确', icon: 'none' })
    return false
  }
  try {
    if (!new URL(url).hostname) {
      uni.showToast({ title: '地址格式不正确', icon: 'none' })
      return false
    }
    uni.setStorageSync(STORAGE_KEY, url)
    return true
  }
  catch {
    uni.showToast({ title: '地址格式不正确', icon: 'none' })
    return false
  }
}

export function resolveAssetUrl(url?: string) {
  if (!url || /^(?:https?:)?\/\//.test(url) || url.startsWith('data:'))
    return url || ''
  const origin = getApiBaseUrl().replace(/\/api\/?$/, '')
  return `${origin}${url.startsWith('/') ? '' : '/'}${url}`
}

export interface ApiResult<T = any> {
  code: number
  message: string
  data: T
}

/** 登录过期时清除本地登录状态，避免残留昵称导致无法重新登录 */
function clearLocalAuth() {
  try {
    uni.removeStorageSync('openid')
    uni.removeStorageSync('sessionToken')
    uni.removeStorageSync('userInfo')
    beginAuthRecovery()
  }
  catch {}
}

function isAuthExpired(res: any): boolean {
  if (res?.statusCode === 401)
    return true
  const data = res?.data
  return data?.code === 401 || /登录.*过期|未登录|请重新登录/.test(data?.message || '')
}

function authHeader() {
  const token = uni.getStorageSync('sessionToken')
  return token ? { 'X-Session-Token': String(token) } : {}
}

/** 统一响应处理：检测登录过期并清除本地状态 */
function handleResponse<T>(res: any, resolve: (v: T) => void, reject: (e: Error) => void) {
  if (isAuthExpired(res)) {
    clearLocalAuth()
    reject(new Error('登录已过期，请重新登录'))
    return
  }
  const data = res.data as ApiResult<T>
  if (data && data.code === 0) {
    resolve(data.data)
  }
  else {
    reject(new Error(data?.message || '请求失败'))
  }
}

/** 默认请求超时（毫秒）。GET/PUT/DELETE 统一使用；POST 涉及 AI 生图等较长链路，保留更长超时 */
const DEFAULT_TIMEOUT = 15000

interface RequestOptions {
  url: string
  method: 'GET' | 'POST' | 'PUT' | 'DELETE'
  data?: any
  params?: Record<string, any>
  timeout?: number
}

function uniRequest<T>(opts: RequestOptions): Promise<T> {
  const query = opts.params
    ? `?${Object.entries(opts.params)
      .filter(([, v]) => v !== undefined && v !== null)
      .map(([k, v]) => `${k}=${encodeURIComponent(String(v))}`)
      .join('&')}`
    : ''
  return new Promise((resolve, reject) => {
    uni.request({
      url: getApiBaseUrl() + opts.url + query,
      method: opts.method,
      timeout: opts.timeout ?? DEFAULT_TIMEOUT,
      data: opts.data,
      header: { 'Content-Type': 'application/json', ...authHeader() },
      success: (res: any) => handleResponse(res, resolve, reject),
      fail: err => reject(new Error(err.errMsg || '网络错误')),
    })
  })
}

/** 通用 GET 请求 */
export function get<T = any>(url: string, params?: Record<string, any>, timeout?: number): Promise<T> {
  return uniRequest<T>({ url, method: 'GET', params, timeout })
}

/** 通用 POST 请求（默认超时较长，适配 AI 生图/长链路接口） */
export function post<T = any>(url: string, data?: any, timeout = 60000, params?: Record<string, any>): Promise<T> {
  return uniRequest<T>({ url, method: 'POST', data, timeout, params })
}

/** 通用 PUT 请求 */
export function put<T = any>(url: string, data?: any, timeout?: number, params?: Record<string, any>): Promise<T> {
  return uniRequest<T>({ url, method: 'PUT', data, timeout, params })
}

/** 通用 DELETE 请求 */
export function del<T = any>(url: string, data?: any, timeout?: number, params?: Record<string, any>): Promise<T> {
  return uniRequest<T>({ url, method: 'DELETE', data, timeout, params })
}

/**
 * 确保上传路径可用：chooseAvatar 等回调可能给出远程图片 URL（如 thirdwx.qlogo.cn），
 * uni.uploadFile 只接受本地临时路径，远程 URL 需先下载为临时文件。
 */
function ensureUploadablePath(filePath: string): Promise<string> {
  // 本地临时路径直接用：wxfile://、http(s)://tmp/（真机/工具临时目录）、blob:/data:（H5）、非 http 协议
  if (!/^https?:\/\//i.test(filePath) || /^https?:\/\/tmp\//i.test(filePath))
    return Promise.resolve(filePath)
  console.log('[upload] 远程图片路径，先下载为临时文件:', filePath)
  return new Promise((resolve, reject) => {
    uni.downloadFile({
      url: filePath,
      success: (res: any) => {
        if (res.statusCode === 200 && res.tempFilePath) {
          console.log('[upload] 远程图片下载完成:', res.tempFilePath)
          resolve(res.tempFilePath)
        }
        else {
          console.warn('[upload] 远程图片下载失败: HTTP', res.statusCode)
          reject(new Error(`素材下载失败(HTTP ${res.statusCode})`))
        }
      },
      fail: err => reject(new Error(err.errMsg || '素材下载失败')),
    })
  })
}

/**
 * 文件上传
 * @param type 上传场景：image=菜品/记录图片（COS 内 mood-recipe/uploads/），avatar=用户头像（mood-recipe/avatar/）
 */
export function uploadFile(filePath: string, type: 'image' | 'avatar' = 'image'): Promise<{ url: string, filename: string }> {
  const startedAt = Date.now()
  return ensureUploadablePath(filePath).then(localPath => new Promise((resolve, reject) => {
    console.log(`[upload] 开始上传 type=${type} filePath=${localPath}`)
    uni.uploadFile({
      url: `${getApiBaseUrl()}/upload/image?type=${type}`,
      filePath: localPath,
      name: 'file',
      // 真机弱网下默认无超时会一直 pending，界面卡「上传中」；60s 后明确失败
      timeout: 60000,
      header: authHeader(),
      success: (res: any) => {
        console.log(`[upload] 收到响应 HTTP ${res.statusCode} 耗时 ${((Date.now() - startedAt) / 1000).toFixed(1)}s`)
        if (isAuthExpired(res)) {
          clearLocalAuth()
          reject(new Error('登录已过期，请重新登录'))
          return
        }
        try {
          const result = JSON.parse(res.data) as ApiResult<{ url: string, filename: string }>
          if (result.code === 0) {
            console.log('[upload] 上传成功 url=', result.data?.url)
            resolve({ ...result.data, url: resolveAssetUrl(result.data.url) })
          }
          else {
            console.warn('[upload] 业务失败:', result.code, result.message)
            reject(new Error(result.message || `上传失败(HTTP ${res.statusCode})`))
          }
        }
        catch {
          const snippet = typeof res.data === 'string' ? res.data.slice(0, 80) : ''
          console.warn('[upload] 响应非 JSON:', res.statusCode, snippet)
          reject(new Error(`上传响应解析失败(HTTP ${res.statusCode})${snippet ? `: ${snippet}` : ''}`))
        }
      },
      fail: (err) => {
        console.warn(`[upload] 请求失败 耗时 ${((Date.now() - startedAt) / 1000).toFixed(1)}s:`, err.errMsg)
        reject(new Error(err.errMsg || '上传失败'))
      },
    })
  }))
}
