/**
 * 系统信息获取的安全封装。
 *
 * wx.getSystemInfoSync 已在基础库 2.20.1 起废弃，官方拆分为多个细粒度接口：
 *   - getWindowInfo()  -> pixelRatio / statusBarHeight / windowWidth 等窗口信息
 *   - getAppBaseInfo() -> theme（深色模式）等小程序基础信息
 * 这里在微信端优先用新接口（消除 deprecation 告警），其余端回退到 getSystemInfoSync。
 * 返回 any 以兼容各端的字段差异，调用方按需取字段即可。
 */
declare const wx: any

function wxReady(): boolean {
  return typeof wx !== 'undefined' && wx && typeof wx.getWindowInfo === 'function'
}

export function getWindowInfo(): any {
  if (wxReady())
    return wx.getWindowInfo()
  return uni.getSystemInfoSync()
}

export function getAppBaseInfo(): any {
  if (typeof wx !== 'undefined' && wx && typeof wx.getAppBaseInfo === 'function')
    return wx.getAppBaseInfo()
  return uni.getSystemInfoSync()
}
