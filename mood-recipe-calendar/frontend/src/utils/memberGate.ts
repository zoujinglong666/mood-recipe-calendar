/**
 * 会员门禁助手：拦截「纯会员能力」入口。
 *
 * 用法（在页面里）：
 *   import { requireMember } from '@/utils/memberGate'
 *   if (!requireMember('fridge_recognize')) return   // 非会员：弹开通引导，且不执行后续动作
 *
 * - 会员：返回 true，调用方照常执行动作。
 * - 非会员：弹出对应 gate 的引导弹窗（确认→跳会员页），返回 false。
 */
import { MEMBER_GATES, type MemberGateKey } from '@/config/memberGates'
import { useUserStore } from '@/stores/user'

/** 当前用户是否为有效会员（统一来源，等价于 userStore.isActiveMember） */
export function isActiveMember(): boolean {
  return useUserStore().isActiveMember
}

/** 拦截会员专享动作。会员放行；非会员弹开通引导并返回 false。 */
export function requireMember(key: MemberGateKey, opts?: { onOpen?: () => void }): boolean {
  const userStore = useUserStore()
  if (userStore.isActiveMember)
    return true

  const gate = MEMBER_GATES[key]
  uni.showModal({
    title: gate.title,
    content: gate.content,
    confirmText: '去开通',
    cancelText: '暂不',
    confirmColor: '#EF5A3C',
    success: (res) => {
      if (res.confirm) {
        if (opts?.onOpen)
          opts.onOpen()
        else
          uni.navigateTo({ url: '/pages/membership/index' })
      }
    },
  })
  return false
}
