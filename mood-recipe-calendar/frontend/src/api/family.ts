import { get, post } from './request'

export interface FamilyMember {
  openid: string
  nickname: string
  avatarUrl: string | null
  role: 'OWNER' | 'MEMBER'
}

export interface FamilyInfo {
  groupId: number
  name: string
  inviteCode: string
  ownerOpenid: string
  /** 家庭创建者当前是否为有效会员 */
  ownerIsMember: boolean
  /** 我当前是否享有会员权益（本人会员或家庭共享生效） */
  myAccessActive: boolean
  myRole: 'OWNER' | 'MEMBER'
  members: FamilyMember[]
}

export function fetchFamily() {
  return get<FamilyInfo | null>('/family')
}

export function createFamily() {
  return post<FamilyInfo>('/family')
}

export function joinFamily(code: string) {
  return post<FamilyInfo>('/family/join', { code })
}

export function leaveFamily() {
  return post('/family/leave')
}
