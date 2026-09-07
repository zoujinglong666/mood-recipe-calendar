/**
 * 统一 Toast 提示工具
 * 抽象 uni.showToast，统一错误/成功/信息提示的调用方式
 */

/** 普通信息提示（无图标） */
export function toast(msg: string, duration = 2000) {
  uni.showToast({ title: msg, icon: 'none', duration })
}

/** 成功提示（带对勾图标） */
export function toastSuccess(msg: string, duration = 1500) {
  uni.showToast({ title: msg, icon: 'success', duration })
}

/**
 * 错误提示
 * - 传入 Error 对象时自动提取 message，无 message 时使用 fallback
 * - 传入字符串时直接显示
 */
export function toastError(err: unknown, fallback = '操作失败，请稍后重试') {
  const msg = err instanceof Error
    ? err.message || fallback
    : typeof err === 'string' && err
      ? err
      : fallback
  uni.showToast({ title: msg, icon: 'none', duration: 2500 })
}

/** 加载中提示（需配合 hideLoading 使用） */
export function showLoading(msg = '加载中...') {
  uni.showLoading({ title: msg, mask: true })
}

export function hideLoading() {
  uni.hideLoading()
}
