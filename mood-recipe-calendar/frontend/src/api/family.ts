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

// ===== 家庭/双人共享菜单：成员各自的当前周计划 + 每天谁掌勺 =====

export interface FamilyDayMenu {
  dayIndex: number
  day: string
  dishName: string
  /** 掌勺人 openid；null = 未定 */
  cookOpenid: string | null
  cookNickname: string | null
}

export interface FamilyMemberMenu {
  openid: string
  nickname: string
  avatarUrl: string | null
  role: 'OWNER' | 'MEMBER'
  /** 该成员当前周计划 id；null = 还没生成过 */
  planId: number | null
  daysCount: number
  days: FamilyDayMenu[]
}

export interface FamilyMenusView {
  inFamily: boolean
  members: FamilyMemberMenu[]
}

export function fetchFamilyMenus() {
  return get<FamilyMenusView>('/family/menus')
}

/** 设置某天掌勺人（cookOpenid 传 null = 未定）；返回更新后的共享视图。 */
export function assignFamilyCook(planId: number, dayIndex: number, cookOpenid: string | null) {
  return post<FamilyMenusView>('/family/assign', { planId, dayIndex, cookOpenid })
}
