/**
 * 触感反馈工具：把 uni.vibrateShort 包成安全调用，避免不支持的平台抛错打断交互。
 * - light：普通点按（卡片/按钮/切换），轻点一下
 * - medium：有明确结果的时刻（中奖落定、换到一道新菜），稍重一点
 * 与现有用法保持一致（翻牌中奖为 medium）。
 */
export type HapticType = 'light' | 'medium' | 'heavy'

export function haptic(type: HapticType = 'light') {
  try {
    uni.vibrateShort({ type })
  }
  catch {
    /* 部分平台/环境下无振动能力，静默降级，不影响主流程 */
  }
}
