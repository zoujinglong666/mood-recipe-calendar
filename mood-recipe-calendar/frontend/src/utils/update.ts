/**
 * 小程序更新管理（基于微信原生 getUpdateManager）。
 *
 * 零维护：新版本在微信后台上传后由微信自动分发，代码侧无需任何版本号配置。
 * - autoCheckUpdate：在 App.onLaunch 中调用，静默触发后台下载，下载完成后弹窗提示重启。
 * - manualCheckUpdate：设置页「检查更新」按钮调用，用户主动检查。
 */

let manager: any = null
let updateReady = false

function ensureManager(): any {
  // #ifdef MP-WEIXIN
  if (!manager) {
    manager = uni.getUpdateManager()
    manager.onUpdateReady(() => {
      updateReady = true
      promptApply()
    })
    manager.onUpdateFailed(() => {
      // 下载失败：静默，下次启动会自动重试
      console.warn('[update] 新版本下载失败')
    })
  }
  // #endif
  return manager
}

function promptApply() {
  uni.showModal({
    title: '发现新版本',
    content: '锅仔已准备好新版本，重启后即可体验最新功能与优化。',
    confirmText: '立即更新',
    confirmColor: '#EF5A3C',
    success: (res) => {
      if (res.confirm)
        manager?.applyUpdate()
    },
  })
}

/** 启动时静默自检：发现新版本会在后台自动下载，下载完成后提示重启 */
export function autoCheckUpdate() {
  const m = ensureManager()
  if (!m)
    return
  m.onCheckForUpdate(() => {
    // 仅触发检查，微信会在后台下载，onUpdateReady 接管后续提示
  })
}

/** 设置页手动检查 */
export function manualCheckUpdate() {
  const m = ensureManager()
  if (!m) {
    uni.showToast({ title: '已是最新版本', icon: 'success' })
    return
  }
  if (updateReady) {
    promptApply()
    return
  }
  m.onCheckForUpdate((res: { hasUpdate: boolean }) => {
    if (res.hasUpdate)
      uni.showToast({ title: '发现新版本，正在准备…', icon: 'none' })
    else
      uni.showToast({ title: '已是最新版本', icon: 'success' })
  })
}
