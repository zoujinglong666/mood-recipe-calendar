/**
 * 智能排版引擎（前端版）
 * 根据图片数量自动选择布局模板，返回相对内容区的布局盒（坐标/宽高，同单位）。
 *
 * 布局规则（与画册样张一致）：
 *   1 张 -> 居中大图
 *   2 张 -> 左右对半
 *   3 张 -> 一大两小（左大右小，杂志风）
 *   4 张 -> 2×2 网格
 *   5+ 张 -> 上 1 大图 + 下 2×2（杂志风）
 */

export interface LayoutBox {
  x: number
  y: number
  w: number
  h: number
}

export interface LayoutArea {
  x: number
  y: number
  w: number
  h: number
}

export const ALBUM_PAGE_SIZE = 5

/**
 * 计算 N 张图的自动布局
 * @param count 图片数量
 * @param area 内容区（相对坐标，需与传入单位一致，如 rpx）
 * @param gap 图间距
 */
export function autoLayout(count: number, area: LayoutArea, gap = 24): LayoutBox[] {
  const { x: ax, y: ay, w: aw, h: ah } = area
  const boxes: LayoutBox[] = []

  if (count <= 0) return []

  if (count === 1) {
    boxes.push({ x: ax, y: ay, w: aw, h: ah })
  } else if (count === 2) {
    const w = (aw - gap) / 2
    boxes.push(
      { x: ax, y: ay, w, h: ah },
      { x: ax + w + gap, y: ay, w, h: ah },
    )
  } else if (count === 3) {
    const wL = aw * 0.58
    const wR = aw - wL - gap
    const hR = (ah - gap) / 2
    boxes.push(
      { x: ax, y: ay, w: wL, h: ah },
      { x: ax + wL + gap, y: ay, w: wR, h: hR },
      { x: ax + wL + gap, y: ay + hR + gap, w: wR, h: hR },
    )
  } else if (count === 4) {
    const w = (aw - gap) / 2
    const h = (ah - gap) / 2
    boxes.push(
      { x: ax, y: ay, w, h },
      { x: ax + w + gap, y: ay, w, h },
      { x: ax, y: ay + h + gap, w, h },
      { x: ax + w + gap, y: ay + h + gap, w, h },
    )
  } else {
    // count >= 5：上 1 大图 + 下 2×2
    const bigH = ah * 0.52
    const smallH = (ah - bigH - gap) / 2
    const smallW = (aw - gap) / 2
    boxes.push(
      { x: ax, y: ay, w: aw, h: bigH },
      { x: ax, y: ay + bigH + gap, w: smallW, h: smallH },
      { x: ax + smallW + gap, y: ay + bigH + gap, w: smallW, h: smallH },
      { x: ax, y: ay + bigH + gap + smallH + gap, w: smallW, h: smallH },
      { x: ax + smallW + gap, y: ay + bigH + gap + smallH + gap, w: smallW, h: smallH },
    )
  }

  // 不足则补齐（兜底，正常不会触发）
  while (boxes.length < count) boxes.push({ x: ax, y: ay, w: 1, h: 1 })
  return boxes.slice(0, count)
}

/**
 * 将一组条目按每页 N 条切分，供每日记录分页使用
 */
export function chunkPages<T>(items: T[], pageSize = ALBUM_PAGE_SIZE): T[][] {
  const pages: T[][] = []
  for (let i = 0; i < items.length; i += pageSize) {
    pages.push(items.slice(i, i + pageSize))
  }
  return pages
}

/** 心情 -> 展示 emoji（与全局统一） */
export const MOOD_EMOJI: Record<string, string> = {
  开心: '😊',
  平静: '😌',
  疲惫: '😔',
  焦虑: '😤',
  难过: '😢',
  嘴馋: '🤤',
  低落: '🌧️',
  想家: '❤️',
}

/** 心情 -> 色值（用于色块） */
export const MOOD_COLOR: Record<string, string> = {
  开心: '#FFD93D',
  平静: '#6BCB77',
  疲惫: '#B5D4E8',
  焦虑: '#E8836B',
  难过: '#E8836B',
  嘴馋: '#FFB88C',
  低落: '#B5D4E8',
  想家: '#FFD93D',
}
