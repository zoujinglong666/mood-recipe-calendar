import { del, get, post, put } from './request'

export type FridgeStatus = 'FRESH' | 'SOON' | 'EXPIRED' | 'NO_DATE'

export interface FridgeItem {
  id: number
  name: string
  quantity: number
  unit: string
  purchasedOn?: string | null
  expiresOn?: string | null
  note?: string | null
  status: FridgeStatus
  daysLeft?: number | null
}

export interface FridgeSummary {
  total: number
  soon: number
  expired: number
  priorityItems: FridgeItem[]
}

export interface FridgeItemPayload {
  name: string
  quantity: number
  unit: string
  purchasedOn?: string
  expiresOn?: string
  note?: string
}

export function fetchFridgeItems() {
  return get<FridgeItem[]>('/fridge/items')
}

export function fetchFridgeSummary() {
  return get<FridgeSummary>('/fridge/summary')
}

export function createFridgeItem(data: FridgeItemPayload) {
  return post<FridgeItem>('/fridge/items', data)
}

export function updateFridgeItem(id: number, data: FridgeItemPayload) {
  return put<FridgeItem>(`/fridge/items/${id}`, data)
}

export function consumeFridgeItem(id: number, amount = 1) {
  return post<FridgeItem | null>(`/fridge/items/${id}/consume`, { amount })
}

export function deleteFridgeItem(id: number) {
  return del<void>(`/fridge/items/${id}`)
}
