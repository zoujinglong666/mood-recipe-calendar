/**
 * 统一请求封装
 * 后端统一返回 { code, message, data }
 */
export const BASE_URL = 'http://localhost:8080/api'

export interface ApiResult<T = any> {
  code: number
  message: string
  data: T
}

/**
 * 通用 GET 请求
 */
export function get<T = any>(url: string, params?: Record<string, any>): Promise<T> {
  return new Promise((resolve, reject) => {
    const query = params
      ? '?' + Object.entries(params)
          .filter(([, v]) => v !== undefined && v !== null)
          .map(([k, v]) => `${k}=${encodeURIComponent(String(v))}`)
          .join('&')
      : ''
    uni.request({
      url: BASE_URL + url + query,
      method: 'GET',
      success: (res: any) => {
        const data = res.data as ApiResult<T>
        if (data && data.code === 0) {
          resolve(data.data)
        } else {
          reject(new Error(data?.message || '请求失败'))
        }
      },
      fail: (err) => reject(new Error(err.errMsg || '网络错误')),
    })
  })
}

/**
 * 通用 POST 请求
 */
export function post<T = any>(url: string, data?: any): Promise<T> {
  return new Promise((resolve, reject) => {
    uni.request({
      url: BASE_URL + url,
      method: 'POST',
      data,
      header: { 'Content-Type': 'application/json' },
      success: (res: any) => {
        const result = res.data as ApiResult<T>
        if (result && result.code === 0) {
          resolve(result.data)
        } else {
          reject(new Error(result?.message || '请求失败'))
        }
      },
      fail: (err) => reject(new Error(err.errMsg || '网络错误')),
    })
  })
}

/**
 * 通用 PUT 请求
 */
export function put<T = any>(url: string, data?: any): Promise<T> {
  return new Promise((resolve, reject) => {
    uni.request({
      url: BASE_URL + url,
      method: 'PUT',
      data,
      header: { 'Content-Type': 'application/json' },
      success: (res: any) => {
        const result = res.data as ApiResult<T>
        if (result && result.code === 0) {
          resolve(result.data)
        } else {
          reject(new Error(result?.message || '请求失败'))
        }
      },
      fail: (err) => reject(new Error(err.errMsg || '网络错误')),
    })
  })
}

/**
 * 通用 DELETE 请求
 */
export function del<T = any>(url: string): Promise<T> {
  return new Promise((resolve, reject) => {
    uni.request({
      url: BASE_URL + url,
      method: 'DELETE',
      success: (res: any) => {
        const result = res.data as ApiResult<T>
        if (result && result.code === 0) {
          resolve(result.data)
        } else {
          reject(new Error(result?.message || '请求失败'))
        }
      },
      fail: (err) => reject(new Error(err.errMsg || '网络错误')),
    })
  })
}

/**
 * 文件上传
 */
export function uploadFile(filePath: string): Promise<{ url: string; filename: string }> {
  return new Promise((resolve, reject) => {
    uni.uploadFile({
      url: BASE_URL + '/upload/image',
      filePath,
      name: 'file',
      success: (res: any) => {
        try {
          const result = JSON.parse(res.data) as ApiResult<{ url: string; filename: string }>
          if (result.code === 0) {
            resolve(result.data)
          } else {
            reject(new Error(result.message || '上传失败'))
          }
        } catch {
          reject(new Error('上传响应解析失败'))
        }
      },
      fail: (err) => reject(new Error(err.errMsg || '上传失败')),
    })
  })
}
