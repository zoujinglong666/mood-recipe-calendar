/**
 * 会员门禁的单一声明源（single source of truth）。
 *
 * 哪些入口算「纯会员能力」、非会员不可触，全部集中在此处维护：
 * 新增 / 调整会员专享入口，只改这个文件 + 在对应页面用 requireMember(key) 接线即可，
 * 不需要在每个页面重复写判定逻辑或弹窗文案。
 *
 * 呈现策略（产品决策）：非会员看到入口时带「会员」锁标、可点，
 * 点击后弹开通引导（不隐藏入口，兼顾门禁与转化）。
 */

export type MemberGateKey =
  | 'fridge_recognize'      // 我的冰箱：拍照识别食材
  | 'fridge_expiry'         // 我的冰箱：食材临期提醒
  | 'meal_agent_web_search' // 锅仔管饭：联网搜索

export interface MemberGateConfig {
  /** 弹窗标题 */
  title: string
  /** 弹窗正文 */
  content: string
}

export const MEMBER_GATES: Record<MemberGateKey, MemberGateConfig> = {
  fridge_recognize: {
    title: '会员专享功能',
    content: '冰箱拍照识别（自动认食材、算保质期、临期提醒）为会员功能，开通后即可使用。',
  },
  fridge_expiry: {
    title: '会员专享功能',
    content: '食材临期提醒为会员功能，开通后即可使用。',
  },
  meal_agent_web_search: {
    title: '会员专享功能',
    content: '锅仔联网搜索（问到菜谱库外的知识也能答）为会员功能，开通后即可使用。',
  },
}
