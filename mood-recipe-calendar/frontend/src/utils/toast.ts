/**
 * 统一 Toast 提示工具
 * 抽象 uni.showToast，统一错误/成功/信息提示的调用方式
 */

import { beginAuthRecovery } from './authRecovery'

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
  // 完整原始错误打进控制台（toast 只显示抽象文案），真机 vConsole 可按 [toastError] 过滤排查
  if (err !== undefined && err !== null)
    console.warn('[toastError]', fallback, '->', err)
  else
    console.warn('[toastError]', fallback)
  let msg = fallback
  let isCancel = false
  if (err instanceof Error) {
    msg = err.message || fallback
  }
  else if (err && typeof err === 'object') {
    const obj = err as Record<string, unknown>
    const raw = obj.errMsg ?? obj.err_msg ?? obj.message
    if (typeof raw === 'string' && raw) {
      msg = raw
      isCancel = /cancel/i.test(raw)
    }
  }
  else if (typeof err === 'string' && err) {
    msg = err
    isCancel = /cancel/i.test(err)
  }
  // 底层网络/超时错误统一抽象成用户能看懂的文案，不暴露 request:fail 等原始串；
  // 上传类错误（uploadFile:fail ...）原样透出，否则超时/域名问题无法排查
  if (!msg || /request:fail|network|socket|ERR_CONNECTION/i.test(msg))
    msg = fallback
  if (isCancel)
    msg = '已取消支付'
  // 未登录错误（ensureLogin 在主动退出后拒绝自动登录时抛出）：绝不透出原始码，
  // 统一转成可读提示，并复用「登录恢复」流程——保存当前页为回跳目标后跳登录页，
  // 登录成功后 consumeAuthReturn 会把用户带回原页面。所有页面经此统一修复，
  // 不再出现「toast 提示了 NOT_LOGGED_IN 却停在原地」的情况。
  if (/^NOT_LOGGED_IN$/.test(msg)) {
    uni.showToast({ title: '请先登录', icon: 'none', duration: 2000 })
    beginAuthRecovery()
    return
  }
  uni.showToast({ title: msg, icon: 'none', duration: 2500 })
}

/** 加载中提示（需配合 hideLoading 使用） */
export function showLoading(msg = '加载中...') {
  uni.showLoading({ title: msg, mask: true })
}

export function hideLoading() {
  uni.hideLoading()
}
