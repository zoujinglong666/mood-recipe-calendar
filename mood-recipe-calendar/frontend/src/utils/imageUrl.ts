/**
 * 图片变体（数据万象 imageMogr2）：把本项目 CDN 图片按「展示用途」取尺寸与格式，
 * 前端层面砍 CDN 流量。三条铁律：
 * 1. 同一逻辑图片在全站用同一变体 → CDN 缓存键一致，命中率最大化（预热与显示必须同 URL）；
 * 2. 只处理本项目 CDN 的图（微信头像 thirdwx.qlogo.cn 等外部 URL 原样返回）；
 * 3. 不放大：源图小于目标尺寸时数据万象原样透传。
 *
 * 背景：COS 源站此前无 Cache-Control，客户端每次冷启动全量条件请求；
 * 变体 + 上传侧 immutable 缓存头双管齐下，二次启动起本地缓存直出。
 */

const CDN_PREFIX = 'https://static.image-zero.art/'

/** 是否为本项目 CDN 图片（且未带处理参数——已处理过的 URL 不重复拼） */
function isCdnImage(url: string): boolean {
  return !!url && url.startsWith(CDN_PREFIX) && !url.includes('?')
}

/** 拼数据万象处理参数 */
function variant(url: string, ops: string): string {
  return isCdnImage(url) ? `${url}?imageMogr2/${ops}` : url
}

/**
 * 小缩略图：240px WebP q80。
 * 用途：首页月历格子、最近记录、周菜单偷看、冰箱组合行、翻牌结果图。
 * 实测 345KB 上传原图 → ~7KB（-98%），1.2MB 菜品 PNG → ~7KB。
 */
export function imgThumb(url?: string): string {
  return variant(url || '', 'thumbnail/240x/format/webp/quality/80')
}

/**
 * 列表/卡面中图：800px WebP q82。
 * 用途：时光机照片、食谱卡、记录详情九宫格、翻牌卡面、菜品池预热。
 * 覆盖 3x 屏下 300rpx（≈450 设备像素）的显示需求，留一倍余量。
 */
export function imgList(url?: string): string {
  return variant(url || '', 'thumbnail/800x/format/webp/quality/82')
}

/**
 * 全幅主图：仅转 WebP，不缩放。
 * 用途：推荐页 hero、三餐页通栏大图——这些位置图要铺满，缩尺寸会露馅。
 * 实测 1.2MB PNG → 66KB（-95%），画质损失肉眼不可辨。
 */
export function imgHero(url?: string): string {
  return variant(url || '', 'format/webp')
}
