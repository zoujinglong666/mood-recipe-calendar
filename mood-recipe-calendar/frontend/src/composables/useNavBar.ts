import { reactive } from 'vue'

export interface NavMetrics {
  /** 状态栏高度（px） */
  statusBarHeight: number
  /** 自定义导航栏总高度 = 状态栏 + 胶囊内容区（px） */
  navBarHeight: number
  /** 胶囊宽度（px） */
  capsuleWidth: number
  /** 胶囊高度（px） */
  capsuleHeight: number
  /** 胶囊左边缘距屏幕左边距离（px） */
  capsuleLeft: number
  /** 右侧需为胶囊预留的间距 = 屏宽 - 胶囊左边缘（px） */
  capsuleRightGap: number
}

const FALLBACK: NavMetrics = {
  statusBarHeight: 20,
  navBarHeight: 64,
  capsuleWidth: 87,
  capsuleHeight: 32,
  capsuleLeft: 269,
  capsuleRightGap: 106,
}

const navMetrics = reactive<NavMetrics>({ ...FALLBACK })

/**
 * 重新计算导航栏度量（状态栏高度 + 胶囊位置），用于适配微信右上角胶囊按钮。
 * - MP-WEIXIN：调用系统 API 精确计算；
 * - 其它端（H5 等）：无状态栏与胶囊，直接收齐。
 */
export function refreshNavMetrics(): void {
  // #ifdef MP-WEIXIN
  try {
    const info = uni.getSystemInfoSync()
    const statusBarHeight = info.statusBarHeight || FALLBACK.statusBarHeight
    const windowWidth = info.windowWidth || 375
    const menu = uni.getMenuButtonBoundingClientRect()
    const capsuleHeight = menu.height || FALLBACK.capsuleHeight
    const capsuleLeft = menu.left || FALLBACK.capsuleLeft
    const navBarHeight = (menu.top - statusBarHeight) * 2 + capsuleHeight

    navMetrics.statusBarHeight = statusBarHeight
    navMetrics.navBarHeight = navBarHeight > 0 ? navBarHeight : FALLBACK.navBarHeight
    navMetrics.capsuleWidth = menu.width || FALLBACK.capsuleWidth
    navMetrics.capsuleHeight = capsuleHeight
    navMetrics.capsuleLeft = capsuleLeft
    navMetrics.capsuleRightGap = Math.max(0, windowWidth - capsuleLeft)
  } catch {
    // 计算失败时保留兜底值
  }
  // #endif

  // 非小程序端（H5 等）：无状态栏与胶囊，直接收齐
  // #ifndef MP-WEIXIN
  navMetrics.statusBarHeight = 0
  navMetrics.navBarHeight = 44
  navMetrics.capsuleWidth = 0
  navMetrics.capsuleHeight = 0
  navMetrics.capsuleLeft = 0
  navMetrics.capsuleRightGap = 0
  // #endif
}

/** 获取导航栏度量（响应式单例）。 */
export function useNavBar() {
  return navMetrics
}

/**
 * 统一返回：有上级页面则 navigateBack，否则回到首页。
 * 供 wd-navbar 的 @click-left 复用（wd-navbar 仅展示箭头，不自动执行返回）。
 */
export function navBack(): void {
  try {
    const pages = getCurrentPages()
    if (pages.length > 1) {
      uni.navigateBack()
      return
    }
  } catch {
    // 忽略页面栈读取失败，走默认回首页
  }
  uni.switchTab({ url: '/pages/index/index' })
}
