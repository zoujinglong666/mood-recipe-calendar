/**
 * 统一维护静态资源 CDN 前缀
 *
 * 所有页面统一从这里引用 COS 资源，避免散落硬编码 URL。
 * 用法：
 *   import { STATIC_BASE_URL, assetUrl } from '@/utils/assets'
 *   模板: :src="STATIC_BASE_URL + '/static/guozai/xxx.png'"
 *   脚本: assetUrl('/static/guozai/xxx.png')
 */

/** 腾讯云 COS 静态资源根（锅仔 IP / 菜品图 / 缺省图等全部在此） */
export const STATIC_BASE_URL = 'https://static.image-zero.art/mood-recipe'

/** 拼接静态资源完整地址，传入以 / 开头的相对路径 */
export function assetUrl(path: string): string {
  if (!path)
    return STATIC_BASE_URL
  return `${STATIC_BASE_URL}${path.startsWith('/') ? '' : '/'}${path}`
}
