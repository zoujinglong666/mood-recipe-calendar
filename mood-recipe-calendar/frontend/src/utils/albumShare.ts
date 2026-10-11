/**
 * 分享长图导出（Canvas 2D）
 * 把画册分享页绘制成竖版长图（9:16 比例），保存到相册。
 * 兼容微信小程序 Canvas 2D（type="2d"）。
 */
import { getCurrentInstance } from 'vue'
import { getWindowInfo } from '@/utils/wxSystem'
import miniProgramCodePath from '../static/share/guozai-miniprogram-code.jpg'

export interface AlbumShareData {
  /** 顶部品牌名 */
  brand: string
  /** 主标题，如「8月干饭日记」 */
  title: string
  /** 记录天数 */
  totalDays: number
  /** 最常做的菜 */
  topDish: string
  /** 最常心情 */
  topMood: string
  /** 底部 slogan */
  slogan: string
  /** 锅仔图片路径（本地 static） */
  guozaiPath: string
  /** 底部提示 */
  footer?: string
  /** 分享图包含 AI 生成内容时显示标识 */
  aiAssisted?: boolean
}

export interface RecipeShareData {
  name: string
  mood: string
  reason: string
  cookingTime?: number
  difficulty?: string
  ingredients?: string[]
  steps?: string[]
  image?: string
  guozaiPath: string
  style?: 'classic' | 'guozai' | 'handwritten'
  source?: 'AI' | 'LOCAL'
}

const COLORS = {
  bg: '#FDF6F0',
  card: '#FDE6D4',
  btn: '#FFB88C',
  accent: '#E8836B',
  yellow: '#FFD93D',
  green: '#6BCB77',
  blue: '#B5D4E8',
  text: '#5A3E2B',
  sub: '#8B6B55',
  border: '#EDD4C0',
  white: '#FFFFFF',
}

// 画布逻辑尺寸（9:16）
const W = 750
const H = 1334

function loadCanvasImage(canvas: any, src: string): Promise<any> {
  return new Promise((resolve, reject) => {
    const img = canvas.createImage()
    img.onload = () => resolve(img)
    img.onerror = () => reject(new Error(`图片加载失败: ${src}`))
    img.src = src
  })
}

function roundRect(ctx: any, x: number, y: number, w: number, h: number, r: number) {
  ctx.beginPath()
  ctx.moveTo(x + r, y)
  ctx.arcTo(x + w, y, x + w, y + h, r)
  ctx.arcTo(x + w, y + h, x, y + h, r)
  ctx.arcTo(x, y + h, x, y, r)
  ctx.arcTo(x, y, x + w, y, r)
  ctx.closePath()
}

function centerText(ctx: any, cx: number, y: number, text: string, size: number, color: string, weight = 'normal') {
  ctx.fillStyle = color
  ctx.font = `${weight} ${size}px sans-serif`
  ctx.textAlign = 'center'
  ctx.textBaseline = 'top'
  ctx.fillText(text, cx, y)
}

/**
 * 绘制分享长图并保存相册
 * @param data 分享数据
 * @param canvasId 页面上的 canvas type=2d 节点 id
 */
export async function exportAlbumShare(data: AlbumShareData, canvasId = 'shareCanvas'): Promise<string> {
  const inst = getCurrentInstance()
  const query = uni.createSelectorQuery()
  const q: any = inst?.proxy ? query.in(inst.proxy) : query

  const canvasInfo = await new Promise<any>((resolve, reject) => {
    q.select(`#${canvasId}`)
      .fields({ node: true, size: true })
      .exec((res: any[]) => {
        if (res && res[0] && res[0].node)
          resolve(res[0])
        else reject(new Error('未找到画布节点'))
      })
  })

  const canvas = canvasInfo.node
  const ctx = canvas.getContext('2d')
  const dpr = (getWindowInfo().pixelRatio || 2)
  canvas.width = W * dpr
  canvas.height = H * dpr
  ctx.scale(dpr, dpr)

  // 背景
  ctx.fillStyle = COLORS.bg
  ctx.fillRect(0, 0, W, H)

  // 装饰圆点
  ctx.fillStyle = COLORS.yellow
  ctx.beginPath()
  ctx.arc(70, 90, 26, 0, Math.PI * 2)
  ctx.fill()
  ctx.fillStyle = COLORS.accent
  ctx.beginPath()
  ctx.arc(W - 80, 150, 22, 0, Math.PI * 2)
  ctx.fill()
  ctx.fillStyle = COLORS.blue
  ctx.beginPath()
  ctx.arc(120, H - 180, 20, 0, Math.PI * 2)
  ctx.fill()

  // 顶部品牌
  centerText(ctx, W / 2, 90, data.brand, 30, COLORS.sub)
  if (data.aiAssisted)
    centerText(ctx, W / 2, 126, '含 AI 生成寄语', 20, COLORS.accent, 'bold')

  // 主标题
  centerText(ctx, W / 2, 170, data.title, 56, COLORS.text, 'bold')

  // 卡片：大数字
  roundRect(ctx, 70, 290, W - 140, 420, 40)
  ctx.fillStyle = COLORS.card
  ctx.fill()

  centerText(ctx, W / 2, 350, '这个月我记录了', 30, COLORS.sub)
  centerText(ctx, W / 2, 400, String(data.totalDays), 130, COLORS.accent, 'bold')
  centerText(ctx, W / 2, 560, '天好饭', 30, COLORS.sub)

  // 信息行
  centerText(ctx, W / 2, 630, `最常做：${data.topDish}`, 30, COLORS.text, 'bold')
  centerText(ctx, W / 2, 680, `心情：${data.topMood}`, 30, COLORS.text, 'bold')

  // 锅仔
  try {
    const gz = await loadCanvasImage(canvas, data.guozaiPath)
    const gSize = 240
    ctx.drawImage(gz, (W - gSize) / 2, 760, gSize, gSize)
  }
  catch {
    // 锅仔加载失败则跳过，不影响导出
  }

  // slogan
  centerText(ctx, W / 2, 1050, data.slogan, 32, COLORS.sub)

  // 分享按钮（圆角胶囊）
  const btnW = 480
  const btnH = 96
  roundRect(ctx, (W - btnW) / 2, 1140, btnW, btnH, 48)
  ctx.fillStyle = COLORS.btn
  ctx.fill()
  centerText(ctx, W / 2, 1166, '扫码看我的心情菜谱日历', 30, COLORS.white, 'bold')

  // 底部
  centerText(ctx, W / 2, 1270, data.footer || '用一道菜，治愈今天的你', 24, COLORS.sub)

  // 导出
  const tempPath = await new Promise<string>((resolve, reject) => {
    uni.canvasToTempFilePath(
      {
        canvasId,
        canvas,
        width: W * dpr,
        height: H * dpr,
        destWidth: W * 2,
        destHeight: H * 2,
        fileType: 'png',
        success: (r: any) => resolve(r.tempFilePath),
        fail: (e: any) => reject(new Error(e.errMsg || '导出失败')),
      },
      inst?.proxy,
    )
  })

  // 保存相册
  await new Promise<void>((resolve, reject) => {
    uni.saveImageToPhotosAlbum({
      filePath: tempPath,
      success: () => resolve(),
      fail: () => reject(new Error('请授权保存到相册后重试')),
    })
  })

  return tempPath
}

function drawCover(ctx: any, image: any, x: number, y: number, width: number, height: number, radius: number) {
  const scale = Math.max(width / image.width, height / image.height)
  const drawWidth = image.width * scale
  const drawHeight = image.height * scale
  ctx.save()
  roundRect(ctx, x, y, width, height, radius)
  ctx.clip()
  ctx.drawImage(image, x + (width - drawWidth) / 2, y + (height - drawHeight) / 2, drawWidth, drawHeight)
  ctx.restore()
}

function drawTiltedPhoto(ctx: any, image: any, x: number, y: number, width: number, height: number) {
  ctx.save()
  ctx.translate(x + width / 2, y + height / 2)
  ctx.rotate(-0.035)
  ctx.fillStyle = '#FFFDF8'
  ctx.fillRect(-width / 2, -height / 2, width, height)
  drawCover(ctx, image, -width / 2 + 14, -height / 2 + 14, width - 28, height - 48, 8)
  ctx.restore()
}

function wrapText(ctx: any, text: string, maxWidth: number, maxLines: number) {
  const chars = String(text || '').trim().slice(0, 120).split('')
  const lines: string[] = []
  let line = ''
  for (const char of chars) {
    if (ctx.measureText(line + char).width > maxWidth && line) {
      lines.push(line)
      line = char
      if (lines.length === maxLines)
        break
    }
    else {
      line += char
    }
  }
  if (line && lines.length < maxLines)
    lines.push(line)
  if (chars.length > lines.join('').length && lines.length)
    lines[lines.length - 1] = `${lines[lines.length - 1].slice(0, -1)}…`
  return lines
}

function leftText(ctx: any, x: number, y: number, text: string, size: number, color: string, weight = 'normal') {
  ctx.fillStyle = color
  ctx.font = `${weight} ${size}px sans-serif`
  ctx.textAlign = 'left'
  ctx.textBaseline = 'top'
  ctx.fillText(text, x, y)
}

function drawRule(ctx: any, y: number) {
  ctx.fillStyle = '#E7D5C7'
  ctx.fillRect(52, y, W - 104, 2)
}

function drawSectionHeading(ctx: any, label: string, title: string, y: number) {
  leftText(ctx, 52, y, label, 17, COLORS.accent, 'bold')
  leftText(ctx, 52, y + 28, title, 30, COLORS.text, 'bold')
}

async function drawRecipeCodeFooter(ctx: any, canvas: any) {
  const footerY = 1224
  drawRule(ctx, footerY)
  leftText(ctx, 52, footerY + 42, 'GUOZAI · MOOD RECIPE', 18, COLORS.accent, 'bold')
  leftText(ctx, 52, footerY + 78, '把今天这一顿，认真做完。', 31, COLORS.text, 'bold')
  leftText(ctx, 52, footerY + 124, '扫码打开小程序，收藏这道菜', 20, COLORS.sub)

  const codeBox = 150
  const codeX = W - 52 - codeBox
  const codeY = footerY + 28
  ctx.fillStyle = COLORS.white
  roundRect(ctx, codeX, codeY, codeBox, codeBox, 16)
  ctx.fill()
  try {
    const code = await loadCanvasImage(canvas, miniProgramCodePath)
    ctx.drawImage(code, codeX + 12, codeY + 12, codeBox - 24, codeBox - 24)
  }
  catch {
    leftText(ctx, codeX + 36, codeY + 62, '扫码做菜', 18, COLORS.sub, 'bold')
  }
}

/** 将当前一条推荐画成可保存的宣传型完整食谱卡；图片资源失败时仍输出文字卡。 */
export async function exportRecipeShare(data: RecipeShareData, canvasId = 'recipeShareCanvas'): Promise<string> {
  const inst = getCurrentInstance()
  const query = uni.createSelectorQuery()
  const q: any = inst?.proxy ? query.in(inst.proxy) : query
  const canvasInfo = await new Promise<any>((resolve, reject) => {
    q.select(`#${canvasId}`).fields({ node: true, size: true }).exec((res: any[]) => {
      if (res?.[0]?.node)
        resolve(res[0])
      else reject(new Error('未找到食谱卡画布'))
    })
  })

  const W = 750
  const H = 1500
  const canvas = canvasInfo.node
  const ctx = canvas.getContext('2d')
  const dpr = getWindowInfo().pixelRatio || 2
  canvas.width = W * dpr
  canvas.height = H * dpr
  ctx.scale(dpr, dpr)
  ctx.fillStyle = COLORS.bg
  ctx.fillRect(0, 0, W, H)

  const isGuozaiStyle = data.style === 'guozai'
  const isHandwrittenStyle = data.style === 'handwritten'
  const heroX = 52
  const heroY = 104
  const heroW = W - 104
  const heroH = 304

  if (isHandwrittenStyle) {
    ctx.fillStyle = '#F0E3D2'
    for (let y = 474; y < 1200; y += 42)
      ctx.fillRect(52, y, W - 104, 1)
    ctx.fillStyle = '#E8836B'
    ctx.fillRect(52, 76, 104, 3)
  }

  leftText(ctx, 52, 48, isHandwrittenStyle ? '锅仔的手写食谱' : isGuozaiStyle ? 'GUOZAI / HOME COOKING' : 'GUOZAI / TODAY’S RECIPE', 18, COLORS.sub, 'bold')
  if (data.source === 'AI')
    leftText(ctx, W - 126, 48, 'AI 生成', 18, COLORS.accent, 'bold')

  ctx.fillStyle = isHandwrittenStyle ? '#F5DEC6' : isGuozaiStyle ? '#F4C99E' : '#EBD7C5'
  roundRect(ctx, heroX, heroY, heroW, heroH, 28)
  ctx.fill()
  if (isGuozaiStyle) {
    try {
      const guozai = await loadCanvasImage(canvas, data.guozaiPath)
      ctx.drawImage(guozai, 408, 116, 220, 220)
    }
    catch { centerText(ctx, 518, 170, '🍲', 96, COLORS.text) }
    ctx.fillStyle = COLORS.white
    roundRect(ctx, 78, 140, 268, 184, 14)
    ctx.fill()
    if (data.image) {
      try {
        drawCover(ctx, await loadCanvasImage(canvas, data.image), 90, 152, 244, 142, 10)
      }
      catch { /* 菜图失败时保留拍立得文字卡。 */ }
    }
  }
  else if (isHandwrittenStyle) {
    if (data.image) {
      try {
        drawTiltedPhoto(ctx, await loadCanvasImage(canvas, data.image), 150, 124, 450, 222)
      }
      catch { /* 菜图失败时保留手写便签底色。 */ }
    }
    else {
      centerText(ctx, W / 2, 178, '今天也要好好吃饭', 28, COLORS.sub, 'bold')
    }
    ctx.fillStyle = '#E8836B'
    ctx.fillRect(112, 352, 82, 3)
  }
  else {
    if (data.image) {
      try {
        drawCover(ctx, await loadCanvasImage(canvas, data.image), heroX, heroY, heroW, heroH, 38)
      }
      catch { /* 菜图失败时保留柔和色块。 */ }
    }
  }
  if (isHandwrittenStyle) {
    ctx.save()
    ctx.translate(W / 2, heroY + heroH - 68)
    ctx.rotate(-0.018)
    centerText(ctx, 0, 0, data.name.slice(0, 15), 42, COLORS.text, 'bold')
    ctx.restore()
  }
  else {
    ctx.fillStyle = isGuozaiStyle ? COLORS.text : 'rgba(43, 29, 21, .68)'
    roundRect(ctx, heroX, heroY + heroH - 92, heroW, 92, 0)
    ctx.fill()
    centerText(ctx, W / 2, heroY + heroH - 68, data.name.slice(0, 15), 42, COLORS.white, 'bold')
  }

  ctx.fillStyle = COLORS.white
  roundRect(ctx, 70, 126, 154, 48, 12)
  ctx.fill()
  centerText(ctx, 147, 140, data.mood ? `今日 · ${data.mood}` : 'GUOZAI · 锅仔推荐', 20, COLORS.text, 'bold')
  centerText(ctx, W / 2, 438, `${data.cookingTime || '--'} 分钟  /  ${data.difficulty || '家常难度'}`, 22, COLORS.sub, 'bold')

  drawSectionHeading(ctx, isHandwrittenStyle ? '锅仔小记 / 今天也要好好吃饭' : 'GUOZAI’S NOTE', isHandwrittenStyle ? '锅仔写给你的话' : '锅仔为什么推荐它', 492)
  ctx.fillStyle = COLORS.accent
  ctx.fillRect(52, 566, 4, 60)
  ctx.font = '24px sans-serif'
  ctx.textAlign = 'left'
  ctx.textBaseline = 'top'
  wrapText(ctx, data.reason, 614, 2).forEach((line, index) => ctx.fillText(line, 74, 564 + index * 31))
  drawRule(ctx, 654)

  drawSectionHeading(ctx, isHandwrittenStyle ? '备料 / 厨房小清单' : 'MATERIALS / 准备好再开火', '材料清单', 682)
  const ingredients = (data.ingredients || []).slice(0, 6)
  ctx.font = '22px sans-serif'
  ingredients.forEach((ingredient, index) => {
    const column = index % 2
    const row = Math.floor(index / 2)
    const x = 52 + column * 326
    const y = 770 + row * 38
    ctx.fillStyle = COLORS.accent
    ctx.beginPath()
    ctx.arc(x + 5, y + 10, 5, 0, Math.PI * 2)
    ctx.fill()
    ctx.fillStyle = COLORS.text
    ctx.textAlign = 'left'
    ctx.fillText(String(ingredient).slice(0, 15), x + 22, y)
  })
  if (!ingredients.length)
    leftText(ctx, 52, 774, '跟着锅仔慢慢做一顿热饭', 22, COLORS.sub)
  drawRule(ctx, 894)

  drawSectionHeading(ctx, isHandwrittenStyle ? '动手 / 一步一步来' : 'COOK / 跟着做就好', '做法', 922)
  const steps = (data.steps || []).slice(0, 4)
  ctx.font = '22px sans-serif'
  steps.forEach((step, index) => {
    const y = 1008 + index * 48
    ctx.fillStyle = COLORS.accent
    ctx.beginPath()
    ctx.arc(66, y + 11, 14, 0, Math.PI * 2)
    ctx.fill()
    centerText(ctx, 66, y + 2, String(index + 1), 17, COLORS.white, 'bold')
    ctx.fillStyle = COLORS.text
    ctx.textAlign = 'left'
    ctx.textBaseline = 'top'
    const line = wrapText(ctx, step, 580, 1)[0] || ''
    ctx.fillText(line, 98, y)
  })
  if (!steps.length)
    leftText(ctx, 52, 1012, '热锅、下料、调味，慢慢做完这一餐。', 22, COLORS.sub)

  await drawRecipeCodeFooter(ctx, canvas)

  return await new Promise<string>((resolve, reject) => {
    uni.canvasToTempFilePath({
      canvasId,
      canvas,
      width: W * dpr,
      height: H * dpr,
      destWidth: W * 2,
      destHeight: H * 2,
      fileType: 'png',
      success: result => resolve(result.tempFilePath),
      fail: error => reject(new Error(error.errMsg || '食谱卡导出失败')),
    }, inst?.proxy)
  })
}

export interface DailyMenuShareMeal {
  name: string
  mealLabel: string
  image: string
  guideline?: { title: string; content: string; source?: string }
}

export interface DailyMenuShareData {
  date: string
  meals: DailyMenuShareMeal[]
  nutrition: { headline: string; tags: string[]; tip: string }
}

function drawTag(ctx: any, text: string, x: number, y: number): number {
  ctx.font = '20px sans-serif'
  const paddingX = 16
  const w = ctx.measureText(text).width + paddingX * 2
  const h = 42
  ctx.fillStyle = COLORS.card
  roundRect(ctx, x, y, w, h, 21)
  ctx.fill()
  ctx.fillStyle = COLORS.sub
  ctx.textAlign = 'left'
  ctx.textBaseline = 'middle'
  ctx.fillText(text, x + paddingX, y + h / 2 + 1)
  ctx.textBaseline = 'top'
  return w + 14
}

/** 把「锅仔的一日三餐」绘成竖版营养卡长图（返回临时文件路径，由调用方保存相册/下载）。 */
export async function exportDailyMenuShare(data: DailyMenuShareData, canvasId = 'dailyMenuShareCanvas'): Promise<string> {
  const inst = getCurrentInstance()
  const query = uni.createSelectorQuery()
  const q: any = inst?.proxy ? query.in(inst.proxy) : query
  const canvasInfo = await new Promise<any>((resolve, reject) => {
    q.select(`#${canvasId}`).fields({ node: true, size: true }).exec((res: any[]) => {
      if (res?.[0]?.node) resolve(res[0])
      else reject(new Error('未找到一日三餐画布'))
    })
  })

  const W = 750
  const headerH = 224
  const nutritionH = 280
  const mealGap = 28
  const mealH = 380
  const footerH = 210
  const totalH = headerH + nutritionH + mealGap + mealH * 3 + mealGap * 3 + footerH

  const canvas = canvasInfo.node
  const ctx = canvas.getContext('2d')
  const dpr = getWindowInfo().pixelRatio || 2
  canvas.width = W * dpr
  canvas.height = totalH * dpr
  ctx.scale(dpr, dpr)

  ctx.fillStyle = COLORS.bg
  ctx.fillRect(0, 0, W, totalH)

  ctx.fillStyle = COLORS.yellow
  ctx.beginPath(); ctx.arc(70, 80, 24, 0, Math.PI * 2); ctx.fill()
  ctx.fillStyle = COLORS.accent
  ctx.beginPath(); ctx.arc(W - 76, 140, 20, 0, Math.PI * 2); ctx.fill()

  centerText(ctx, W / 2, 60, 'GUOZAI · DAILY MENU', 20, COLORS.sub, 'bold')
  centerText(ctx, W / 2, 100, '锅仔的一日三餐', 46, COLORS.text, 'bold')
  centerText(ctx, W / 2, 164, data.date || '', 24, COLORS.sub)

  const nutY = headerH
  ctx.fillStyle = COLORS.card
  roundRect(ctx, 52, nutY, W - 104, nutritionH, 32)
  ctx.fill()
  leftText(ctx, 80, nutY + 28, '今日营养结构', 22, COLORS.accent, 'bold')
  ctx.font = '28px sans-serif'; ctx.textAlign = 'left'; ctx.textBaseline = 'top'
  wrapText(ctx, data.nutrition.headline || '', W - 200, 2).forEach((line, i) => {
    ctx.fillStyle = COLORS.text
    ctx.fillText(line, 80, nutY + 66 + i * 34)
  })
  let tagX = 80
  const tagY = nutY + 150
  for (const t of (data.nutrition.tags || [])) {
    const w = drawTag(ctx, t, tagX, tagY)
    tagX += w
    if (tagX > W - 130) break
  }
  ctx.font = '22px sans-serif'; ctx.textAlign = 'left'; ctx.textBaseline = 'top'
  wrapText(ctx, data.nutrition.tip || '', W - 200, 2).forEach((line, i) => {
    ctx.fillStyle = COLORS.sub
    ctx.fillText(line, 80, nutY + 204 + i * 30)
  })

  let cardY = headerH + nutritionH + mealGap
  for (let i = 0; i < data.meals.length; i++) {
    const meal = data.meals[i]
    ctx.fillStyle = COLORS.white
    roundRect(ctx, 52, cardY, W - 104, mealH, 32)
    ctx.fill()

    const imgX = 76, imgY = cardY + 30, imgW = 200, imgH = 200
    let drawn = false
    if (meal.image) {
      try {
        drawCover(ctx, await loadCanvasImage(canvas, meal.image), imgX, imgY, imgW, imgH, 20)
        drawn = true
      }
      catch { drawn = false }
    }
    if (!drawn) {
      ctx.fillStyle = COLORS.card
      roundRect(ctx, imgX, imgY, imgW, imgH, 20)
      ctx.fill()
      centerText(ctx, imgX + imgW / 2, imgY + imgH / 2 - 22, '锅仔', 30, COLORS.sub, 'bold')
      centerText(ctx, imgX + imgW / 2, imgY + imgH / 2 + 16, '用心搭', 22, COLORS.sub)
    }

    leftText(ctx, 304, cardY + 36, meal.mealLabel, 24, COLORS.accent, 'bold')
    ctx.font = '34px sans-serif'; ctx.textAlign = 'left'; ctx.textBaseline = 'top'
    ctx.fillStyle = COLORS.text
    ctx.fillText(String(meal.name || '').slice(0, 12), 304, cardY + 74)
    leftText(ctx, 304, cardY + 124, '锅仔替你决定', 22, COLORS.sub)

    const g = meal.guideline
    if (g && (g.title || g.content)) {
      ctx.fillStyle = COLORS.accent
      roundRect(ctx, 76, cardY + 248, 96, 36, 18)
      ctx.fill()
      centerText(ctx, 76 + 48, cardY + 257, '有据可依', 19, COLORS.white, 'bold')
      ctx.font = '22px sans-serif'; ctx.textAlign = 'left'; ctx.textBaseline = 'top'
      ctx.fillStyle = COLORS.accent
      ctx.fillText((g.title || '').slice(0, 16), 188, cardY + 254)
      ctx.font = '20px sans-serif'; ctx.fillStyle = COLORS.text
      wrapText(ctx, g.content || '', W - 200, 2).forEach((line, li) => {
        ctx.fillText(line, 76, cardY + 294 + li * 26)
      })
      if (g.source) {
        ctx.fillStyle = COLORS.sub; ctx.font = '18px sans-serif'
        ctx.fillText(`来源：${(g.source || '').slice(0, 22)}`, 76, cardY + 294 + 2 * 26 + 4)
      }
    }
    else {
      leftText(ctx, 76, cardY + 256, '用心搭配的三餐，慢慢吃完。', 22, COLORS.sub)
    }

    cardY += mealH + mealGap
  }

  const footerY = cardY + 8
  drawRule(ctx, footerY)
  leftText(ctx, 52, footerY + 36, 'GUOZAI · MOOD RECIPE', 18, COLORS.accent, 'bold')
  leftText(ctx, 52, footerY + 72, '依据《中国居民膳食指南（2022）》', 28, COLORS.text, 'bold')
  leftText(ctx, 52, footerY + 114, '扫码让锅仔替你决定今天吃什么', 20, COLORS.sub)
  const codeBox = 150
  const codeX = W - 52 - codeBox
  const codeY = footerY + 24
  ctx.fillStyle = COLORS.white
  roundRect(ctx, codeX, codeY, codeBox, codeBox, 16)
  ctx.fill()
  try {
    ctx.drawImage(await loadCanvasImage(canvas, miniProgramCodePath), codeX + 12, codeY + 12, codeBox - 24, codeBox - 24)
  }
  catch {
    leftText(ctx, codeX + 36, codeY + 62, '扫码', 18, COLORS.sub, 'bold')
  }

  return await new Promise<string>((resolve, reject) => {
    uni.canvasToTempFilePath({
      canvasId,
      canvas,
      width: W * dpr,
      height: totalH * dpr,
      destWidth: W * 2,
      destHeight: totalH * 2,
      fileType: 'png',
      success: result => resolve(result.tempFilePath),
      fail: error => reject(new Error(error.errMsg || '一日三餐长图导出失败')),
    }, inst?.proxy)
  })
}

export interface RecordShareData {
  dishName: string
  moodTag: string
  note?: string
  recordDate: string
  cookingTime?: number
  /** 真实菜品照片（已解析为可访问 URL），可多张 */
  photos: string[]
}

/** 把「这一餐的食光」绘成可保存/分享的竖版回忆海报（返回临时文件路径）。 */
export async function exportRecordShare(data: RecordShareData, canvasId = 'recordShareCanvas'): Promise<string> {
  const inst = getCurrentInstance()
  const query = uni.createSelectorQuery()
  const q: any = inst?.proxy ? query.in(inst.proxy) : query
  const canvasInfo = await new Promise<any>((resolve, reject) => {
    q.select(`#${canvasId}`).fields({ node: true, size: true }).exec((res: any[]) => {
      if (res?.[0]?.node) resolve(res[0])
      else reject(new Error('未找到回忆海报画布'))
    })
  })

  const W = 750
  const HEAD = 300
  const MAIN = 520
  const capH = 300
  const footerH = 210
  const totalH = HEAD + MAIN + capH + footerH

  const canvas = canvasInfo.node
  const ctx = canvas.getContext('2d')
  const dpr = getWindowInfo().pixelRatio || 2
  canvas.width = W * dpr
  canvas.height = totalH * dpr
  ctx.scale(dpr, dpr)

  ctx.fillStyle = COLORS.bg
  ctx.fillRect(0, 0, W, totalH)

  ctx.fillStyle = COLORS.yellow
  ctx.beginPath(); ctx.arc(72, 86, 24, 0, Math.PI * 2); ctx.fill()
  ctx.fillStyle = COLORS.accent
  ctx.beginPath(); ctx.arc(W - 78, 148, 20, 0, Math.PI * 2); ctx.fill()

  leftText(ctx, 52, 52, 'FOOD MEMORY', 20, COLORS.accent, 'bold')
  ctx.fillStyle = COLORS.card
  roundRect(ctx, W - 172, 46, 120, 46, 23)
  ctx.fill()
  centerText(ctx, W - 112, 58, (data.moodTag || '这一餐').slice(0, 6), 20, COLORS.accent, 'bold')
  ctx.font = 'bold 50px sans-serif'; ctx.textAlign = 'left'; ctx.textBaseline = 'top'
  ctx.fillStyle = COLORS.text
  wrapText(ctx, data.dishName || '', W - 140, 1).forEach((line) => ctx.fillText(line, 52, 116))
  leftText(ctx, 52, 190, `${data.recordDate || ''}  ·  ${data.cookingTime || 30} 分钟`, 24, COLORS.sub)

  const [main, ...rest] = data.photos
  const imgY = HEAD
  let mainHeight = MAIN
  let drawnMain = false
  if (main) {
    try {
      drawCover(ctx, await loadCanvasImage(canvas, main), 52, imgY, W - 104, MAIN, 28)
      drawnMain = true
    }
    catch { drawnMain = false }
  }
  if (!drawnMain) {
    ctx.fillStyle = COLORS.card
    roundRect(ctx, 52, imgY, W - 104, MAIN, 28)
    ctx.fill()
    centerText(ctx, W / 2, imgY + MAIN / 2 - 30, '锅仔', 40, COLORS.sub, 'bold')
    centerText(ctx, W / 2, imgY + MAIN / 2 + 24, '照片没留住，味道留住了', 24, COLORS.sub)
  }
  else if (rest.length) {
    const stripY = imgY + MAIN + 10
    const stripH = 150
    const cols = Math.min(rest.length, 3)
    const gap = 10
    const cw = (W - 104 - gap * (cols - 1)) / cols
    for (let i = 0; i < cols; i++) {
      try {
        drawCover(ctx, await loadCanvasImage(canvas, rest[i]), 52 + i * (cw + gap), stripY, cw, stripH, 12)
      }
      catch { /* 缩略图失败跳过 */ }
    }
    mainHeight = MAIN + 10 + stripH
  }

  const capY = HEAD + mainHeight + 26
  ctx.fillStyle = COLORS.accent
  roundRect(ctx, 52, capY, 6, 130, 3)
  ctx.fill()
  leftText(ctx, 78, capY + 2, '留下一句记忆', 20, COLORS.accent, 'bold')
  ctx.font = '26px sans-serif'; ctx.textAlign = 'left'; ctx.textBaseline = 'top'
  ctx.fillStyle = COLORS.text
  const noteLines = wrapText(ctx, data.note || '下次回来看，你会记得这顿饭的味道。', W - 190, 3)
  noteLines.forEach((line, i) => ctx.fillText(line, 78, capY + 40 + i * 36))

  const footerY = totalH - footerH
  drawRule(ctx, footerY + 8)
  leftText(ctx, 52, footerY + 42, 'GUOZAI · MOOD RECIPE', 18, COLORS.accent, 'bold')
  leftText(ctx, 52, footerY + 78, '把今天这一顿，认真记住。', 30, COLORS.text, 'bold')
  leftText(ctx, 52, footerY + 124, '扫码打开小程序，记录你的食光', 20, COLORS.sub)
  const codeBox = 150
  const codeX = W - 52 - codeBox
  const codeY = footerY + 28
  ctx.fillStyle = COLORS.white
  roundRect(ctx, codeX, codeY, codeBox, codeBox, 16)
  ctx.fill()
  try {
    ctx.drawImage(await loadCanvasImage(canvas, miniProgramCodePath), codeX + 12, codeY + 12, codeBox - 24, codeBox - 24)
  }
  catch {
    leftText(ctx, codeX + 36, codeY + 62, '扫码', 18, COLORS.sub, 'bold')
  }

  return await new Promise<string>((resolve, reject) => {
    uni.canvasToTempFilePath({
      canvasId,
      canvas,
      width: W * dpr,
      height: totalH * dpr,
      destWidth: W * 2,
      destHeight: totalH * 2,
      fileType: 'png',
      success: result => resolve(result.tempFilePath),
      fail: error => reject(new Error(error.errMsg || '回忆海报导出失败')),
    }, inst?.proxy)
  })
}

/** 保存导出的 PNG；小程序写入相册，H5 触发浏览器下载。 */
export async function saveShareImage(path: string, filename = '锅仔食谱卡.png') {
  // #ifdef H5
  const link = document.createElement('a')
  link.href = path
  link.download = filename
  link.click()
  // #endif
  // #ifndef H5
  await new Promise<void>((resolve, reject) => {
    uni.saveImageToPhotosAlbum({
      filePath: path,
      success: () => resolve(),
      fail: error => reject(new Error(error.errMsg || '请授权保存到相册后重试')),
    })
  })
  // #endif
}
