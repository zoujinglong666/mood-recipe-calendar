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

export const API_BACKENDS: ApiBackend[] = [
  { name: '本地开发', url: 'http://10.18.0.19:8080/api', env: 'development', desc: '10.18.0.19:8080' },
  { name: '生产环境', url: 'https://moodrecipe.icu/api', env: 'production', desc: 'moodrecipe.icu' },
]

const STORAGE_KEY = 'apiBaseUrl'

/** 读取当前后端地址：优先用户手动选择，其次编译环境变量 */
export function getApiBaseUrl(): string {
  try {
    const saved = uni.getStorageSync(STORAGE_KEY)
    if (saved)
      return String(saved)
  }
  catch {}
  return import.meta.env.VITE_API_BASE_URL || 'http://10.18.0.19:8080/api'
}

/** 设置后端地址（持久化到本地，切换后立即生效） */
export function setApiBaseUrl(url: string) {
  try {
    uni.setStorageSync(STORAGE_KEY, url)
  }
  catch {}
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

/**
 * 通用 GET 请求
 */
export function get<T = any>(url: string, params?: Record<string, any>): Promise<T> {
  return new Promise((resolve, reject) => {
    const query = params
      ? `?${Object.entries(params)
        .filter(([, v]) => v !== undefined && v !== null)
        .map(([k, v]) => `${k}=${encodeURIComponent(String(v))}`)
        .join('&')}`
      : ''
    uni.request({
      url: getApiBaseUrl() + url + query,
      method: 'GET',
      header: authHeader(),
      success: (res: any) => handleResponse(res, resolve, reject),
      fail: err => reject(new Error(err.errMsg || '网络错误')),
    })
  })
}

/**
 * 通用 POST 请求
 */
export function post<T = any>(url: string, data?: any): Promise<T> {
  return new Promise((resolve, reject) => {
    uni.request({
      url: getApiBaseUrl() + url,
      method: 'POST',
      timeout: 60000,
      data,
      header: { 'Content-Type': 'application/json', ...authHeader() },
      success: (res: any) => handleResponse(res, resolve, reject),
      fail: err => reject(new Error(err.errMsg || '网络错误')),
    })
  })
}

/**
 * 通用 PUT 请求
 */
export function put<T = any>(url: string, data?: any): Promise<T> {
  return new Promise((resolve, reject) => {
    uni.request({
      url: getApiBaseUrl() + url,
      method: 'PUT',
      data,
      header: { 'Content-Type': 'application/json', ...authHeader() },
      success: (res: any) => handleResponse(res, resolve, reject),
      fail: err => reject(new Error(err.errMsg || '网络错误')),
    })
  })
}

/**
 * 通用 DELETE 请求
 */
export function del<T = any>(url: string): Promise<T> {
  return new Promise((resolve, reject) => {
    uni.request({
      url: getApiBaseUrl() + url,
      method: 'DELETE',
      header: authHeader(),
      success: (res: any) => handleResponse(res, resolve, reject),
      fail: err => reject(new Error(err.errMsg || '网络错误')),
    })
  })
}

/**
 * 文件上传
 * @param type 上传场景：image=菜品/记录图片（COS 内 mood-recipe/uploads/），avatar=用户头像（mood-recipe/avatar/）
 */
export function uploadFile(filePath: string, type: 'image' | 'avatar' = 'image'): Promise<{ url: string, filename: string }> {
  return new Promise((resolve, reject) => {
    uni.uploadFile({
      url: `${getApiBaseUrl()}/upload/image?type=${type}`,
      filePath,
      name: 'file',
      header: authHeader(),
      success: (res: any) => {
        if (isAuthExpired(res)) {
          clearLocalAuth()
          reject(new Error('登录已过期，请重新登录'))
          return
        }
        try {
          const result = JSON.parse(res.data) as ApiResult<{ url: string, filename: string }>
          if (result.code === 0) {
            resolve({ ...result.data, url: resolveAssetUrl(result.data.url) })
          }
          else {
            reject(new Error(result.message || '上传失败'))
          }
        }
        catch {
          reject(new Error('上传响应解析失败'))
        }
      },
      fail: err => reject(new Error(err.errMsg || '上传失败')),
    })
  })
}
