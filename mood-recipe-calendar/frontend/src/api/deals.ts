import { get } from './request'

/**
 * [2026-10-28] 优惠入口（聚推客 CPS）。
 * 后端 /api/deals/* 未配置 apikey 时返回 503，页面据此隐藏入口。
 */

/** 联盟活动（来自 /union/act_list，后端按分类并行拉取 + 30 分钟缓存） */
export interface DealActivity {
  actId: number
  name: string
  desc: string
  /** 活动 icon（聚推客 CDN，可能 http），加载失败回退 emoji */
  icon: string
}

/** 按分类分组（美团/饿了么/连锁餐饮/打车出行/电影票/特惠酒店/快递优惠），空分类不返回 */
export interface DealGroup {
  category: string
  activities: DealActivity[]
}

/** 转链结果（/union/act）。官方明确各字段「部分活动没有」，必须判空。 */
export interface DealLink {
  actName: string
  /** H5 领券短链（直跳小程序不可用时的降级方式：复制后粘贴打开） */
  h5: string
  /** H5 长链（h5 为空时的兜底） */
  longH5: string
  /** 第三方官方小程序信息（优先使用：navigateToMiniProgram 直跳，含跟单参数） */
  miniAppId: string
  miniPath: string
}

export function fetchDeals() {
  return get<DealGroup[]>('/deals/activities')
}

export function fetchDealLink(actId: number) {
  return get<DealLink>('/deals/link', { actId })
}
