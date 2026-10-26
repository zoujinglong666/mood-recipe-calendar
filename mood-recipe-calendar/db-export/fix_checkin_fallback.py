# -*- coding: utf-8 -*-
"""Remove legacy /gallery/checkin fallback from frontend checkin.ts."""
import io, sys

P = r"C:\Users\DELL\DoubaoWork\chats\2026-09-01\new-chat\mood-recipe-calendar\frontend\src\api\checkin.ts"

with io.open(P, "r", encoding="utf-8") as f:
    s = f.read()

old = """/**
 * 新旧后端过渡兼容（2026-10-25）：签到接口从 /api/gallery/checkin（形象馆）迁到 /api/checkin（独立）。
 * 旧后端没有新路径会返 404，此时回退旧路径——保证「小程序先提审、新后端未部署」窗口期内签到可用。
 * 旧后端退役后（GalleryController 已随商城下线删除），把 withLegacyFallback 整个删掉即可。
 */
async function withLegacyFallback<T>(primary: () => Promise<T>, legacy: () => Promise<T>): Promise<T> {
  try {
    return await primary()
  }
  catch (e) {
    if (e instanceof ApiError && e.code === 404)
      return await legacy()
    throw e
  }
}

/** 今日签到 */
export function doCheckin() {
  return withLegacyFallback(
    () => post<CheckinStatus>('/checkin'),
    () => post<CheckinStatus>('/gallery/checkin'),
  )
}

/** 签到状态 */
export function fetchCheckinStatus() {
  return withLegacyFallback(
    () => get<CheckinStatus>('/checkin/status'),
    () => get<CheckinStatus>('/gallery/checkin/status'),
  )
}
"""

new = """/** 今日签到（旧 /gallery/checkin 路径已随画廊下线删除，2026-10-26 移除回退逻辑） */
export function doCheckin() {
  return post<CheckinStatus>('/checkin')
}

/** 签到状态 */
export function fetchCheckinStatus() {
  return get<CheckinStatus>('/checkin/status')
}
"""

if old not in s:
    print("ERROR: fallback block not found in checkin.ts")
    sys.exit(1)
s = s.replace(old, new)

# 清理不再使用的 ApiError 引用（若文件内已无其他使用）
if "ApiError" not in s.replace("import { get, post, ApiError } from './request'", ""):
    s = s.replace("import { get, post, ApiError } from './request'", "import { get, post } from './request'")

with io.open(P, "w", encoding="utf-8", newline="\n") as f:
    f.write(s)
print("OK: checkin.ts fallback removed")
