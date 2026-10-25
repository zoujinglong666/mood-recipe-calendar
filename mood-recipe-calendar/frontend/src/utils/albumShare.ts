/**
 * 分享长图导出（Canvas 2D）
 * 把画册分享页绘制成竖版长图（9:16 比例），保存到相册。
 * 兼容微信小程序 Canvas 2D（type="2d"）。
 */
import { getCurrentInstance } from 'vue'
import { getWindowInfo } from '@/utils/wxSystem'
import { ensureWxacodeUrl } from '@/api/wxacode'
import { STATIC_BASE_URL } from '@/utils/assets'
// 小程序码静态兜底图：后端 wxacode 接口未部署/失败（40066 等）时，海报里放真码，
// 好友扫码照样能进小程序。四级降级 = 后端真码 → COS 静态码 → 本地包静态码 → 文字
const COS_FALLBACK_CODE = STATIC_BASE_URL + '/static/guozai/miniprogram-code.jpg'
import fallbackCodeImg from '@/static/guozai/miniprogram-code.jpg'

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

/** 画「扫码打开小程序」码盒（四级降级：后端真码 → COS 静态码 → 本地静态码 → 文字）。
 *  codeUrl 为后端生成的小程序码（COS 网络图）；缺失或加载失败时依次用 COS 静态码、
 *  本地包静态码兜底，全都失败才落文字——任何情况下盒子里都有可扫的内容，绝不留空白盒。 */
async function drawPosterCode(ctx: any, canvas: any, codeUrl: string, x: number, y: number, box: number, inset = 12) {
  if (codeUrl) {
    try {
      const code = await loadCanvasImage(canvas, codeUrl)
      ctx.drawImage(code, x + inset, y + inset, box - inset * 2, box - inset * 2)
      return
    }
    catch { /* 后端码缺失/加载失败，走静态兜底 */ }
  }
  for (const src of [COS_FALLBACK_CODE, fallbackCodeImg]) {
    if (!src) continue
    try {
      const code = await loadCanvasImage(canvas, src)
      ctx.drawImage(code, x + inset, y + inset, box - inset * 2, box - inset * 2)
      return
    }
    catch { /* 换下一级兜底 */ }
  }
  centerText(ctx, x + box / 2, y + box / 2 - 14, '小程序码', 18, COLORS.sub, 'bold')
  centerText(ctx, x + box / 2, y + box / 2 + 14, '加载中', 16, COLORS.sub)
}

/** 食谱海报页脚的码盒：白底圆角盒 + drawPosterCode 降级链 */
async function drawCodeBox(ctx: any, canvas: any, codeUrl: string, codeY: number) {
  const codeBox = 150
  const codeX = W - 52 - codeBox
  ctx.fillStyle = COLORS.white
  roundRect(ctx, codeX, codeY, codeBox, codeBox, 16)
  ctx.fill()
  await drawPosterCode(ctx, canvas, codeUrl, codeX, codeY, codeBox)
}

async function drawRecipeCodeFooter(ctx: any, canvas: any, codeUrl: string) {
  const footerY = 1224
  drawRule(ctx, footerY)
  leftText(ctx, 52, footerY + 42, 'GUOZAI · MOOD RECIPE', 18, COLORS.accent, 'bold')
  leftText(ctx, 52, footerY + 78, '把今天这一顿，认真做完。', 31, COLORS.text, 'bold')
  leftText(ctx, 52, footerY + 124, '扫码打开小程序，收藏这道菜', 20, COLORS.sub)
  await drawCodeBox(ctx, canvas, codeUrl, footerY + 28)
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
    catch { centerText(ctx, 518, 170, '', 96, COLORS.text) }
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

  await drawRecipeCodeFooter(ctx, canvas, await ensureWxacodeUrl('recipe'))

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
  cookingTime?: number
  difficulty?: string
  guideline?: { title: string; content: string; source?: string }
}

export interface DailyMenuShareData {
  date: string
  meals: DailyMenuShareMeal[]
  nutrition: { headline: string; tags: string[]; tip: string }
}

/**
 * 一日三餐分享海报的专属调色板（与 App 现行设计系统对齐）：
 * 注意不能改共享 COLORS——那会影响画册/食谱/回忆三张海报，本次只重绘三餐这张。
 */
const DMP = {
  bg: '#F8ECDA', // 页面奶油底
  surface: '#FFFCF7', // 卡面近白暖调
  sun: '#FFF8E8', // 高光面
  peach: '#FBEBD6', // 次级面
  text: '#3D2519', // 深咖标题
  body: '#6A4A37', // 暖褐正文
  sub: '#9A7B62', // 弱文字
  light: '#BFA98F', // 最弱文字
  border: '#EBDCC6', // 发丝边
  accent: '#EF5A3C', // 强调珊瑚
  coral: '#FF6B5B', // 主珊瑚
  coralSoft: 'rgba(239, 90, 60, 0.12)',
  mint: '#28C2A0',
  mintSoft: 'rgba(40, 194, 160, 0.12)',
  mintText: '#1B8A6E',
  white: '#FFFFFF',
}
const WEEK_CN = ['周日', '周一', '周二', '周三', '周四', '周五', '周六']

/** 海报刊头日期：「10/25」+「周日」，编辑感来自大数字 */
function dmpDateParts(iso: string): { day: string, week: string } {
  const d = new Date(`${iso || ''}T00:00:00`)
  if (Number.isNaN(d.getTime())) return { day: iso || '', week: '' }
  return { day: `${d.getMonth() + 1}/${d.getDate()}`, week: WEEK_CN[d.getDay()] }
}

/** 把「锅仔的一日三餐」绘成竖版分享长图（返回临时文件路径，由调用方保存相册/下载）。
 *  视觉语言与「今日三餐」页保持一致：奶油底 + 珊瑚主色 + 刊头大日期 + 锅仔印章。 */
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
  const PAD = 52
  const headerH = 320
  const nutritionH = 288
  const mealGap = 28
  const mealMediaH = 300
  const mealBodyH = 196
  const mealH = mealMediaH + mealBodyH
  const footerH = 230
  const { day, week } = dmpDateParts(data.date)
  const totalH = headerH + nutritionH + mealGap + (mealH + mealGap) * data.meals.length + footerH

  const canvas = canvasInfo.node
  const ctx = canvas.getContext('2d')
  const dpr = getWindowInfo().pixelRatio || 2
  canvas.width = W * dpr
  canvas.height = totalH * dpr
  ctx.scale(dpr, dpr)

  // 奶油底 + 顶部暖桃光斑（纯渐变，不用装饰圆点）
  ctx.fillStyle = DMP.bg
  ctx.fillRect(0, 0, W, totalH)
  const glow = ctx.createRadialGradient(W / 2, -40, 40, W / 2, -40, 460)
  glow.addColorStop(0, 'rgba(255, 197, 61, .30)')
  glow.addColorStop(1, 'rgba(255, 197, 61, 0)')
  ctx.fillStyle = glow
  ctx.fillRect(0, 0, W, 420)

  // ---- 刊头：大日期 + 标题 + 副题 + 锅仔印章 ----
  leftText(ctx, PAD, 108, day, 72, DMP.text, 'bold')
  const dayW = ctx.measureText(day).width
  leftText(ctx, PAD + dayW + 18, 152, week, 26, DMP.accent, 'bold')
  leftText(ctx, PAD, 208, '今日三餐', 52, DMP.text, 'bold')
  leftText(ctx, PAD, 276, '忌口与《中国居民膳食指南》，锅仔都替你想好了', 22, DMP.sub)

  // 印章：暖底圆 + 珊瑚细环 + 锅仔插画
  const stampCx = W - PAD - 96
  const stampCy = 176
  const stampR = 92
  ctx.fillStyle = DMP.peach
  ctx.beginPath(); ctx.arc(stampCx, stampCy, stampR, 0, Math.PI * 2); ctx.fill()
  ctx.strokeStyle = 'rgba(239, 90, 60, .25)'
  ctx.lineWidth = 2
  ctx.beginPath(); ctx.arc(stampCx, stampCy, stampR, 0, Math.PI * 2); ctx.stroke()
  try {
    const stamp = await loadCanvasImage(canvas, `${STATIC_BASE_URL}/static/guozai/action_01_bowl.png`)
    ctx.drawImage(stamp, stampCx - 66, stampCy - 66, 132, 132)
  }
  catch { /* 印章加载失败保留暖底圆，不阻断导出 */ }

  // ---- 今日营养达成 ----
  const nutY = headerH
  ctx.fillStyle = DMP.surface
  roundRect(ctx, PAD, nutY, W - PAD * 2, nutritionH, 36)
  ctx.fill()
  ctx.strokeStyle = DMP.border
  ctx.lineWidth = 2
  roundRect(ctx, PAD, nutY, W - PAD * 2, nutritionH, 36)
  ctx.stroke()

  ctx.fillStyle = DMP.accent
  ctx.beginPath(); ctx.arc(PAD + 30, nutY + 44, 7, 0, Math.PI * 2); ctx.fill()
  leftText(ctx, PAD + 48, nutY + 33, '今日营养达成', 24, DMP.accent, 'bold')
  ctx.font = 'bold 28px sans-serif'; ctx.textAlign = 'left'; ctx.textBaseline = 'top'
  ctx.fillStyle = DMP.text
  wrapText(ctx, data.nutrition.headline || '', W - PAD * 2 - 96, 2).forEach((line, i) => {
    ctx.fillText(line, PAD + 30, nutY + 72 + i * 38)
  })

  // 覆盖大类 chips：薄荷底 + 深薄荷字
  let chipX = PAD + 30
  const chipY = nutY + 168
  for (const t of (data.nutrition.tags || [])) {
    ctx.font = '20px sans-serif'
    const w = ctx.measureText(t).width + 36
    if (chipX + w > W - PAD - 30) break
    ctx.fillStyle = DMP.mintSoft
    roundRect(ctx, chipX, chipY, w, 44, 22)
    ctx.fill()
    ctx.fillStyle = DMP.mintText
    ctx.textAlign = 'left'; ctx.textBaseline = 'middle'
    ctx.fillText(t, chipX + 18, chipY + 23)
    ctx.textBaseline = 'top'
    chipX += w + 12
  }

  // TIP：珊瑚规线引用块
  ctx.fillStyle = DMP.accent
  roundRect(ctx, PAD + 30, chipY + 62, 6, 44, 3)
  ctx.fill()
  wrapText(ctx, data.nutrition.tip || '', W - PAD * 2 - 110, 2).forEach((line, i) => {
    leftText(ctx, PAD + 50, chipY + 60 + i * 30, line, 20, DMP.body)
  })

  // ---- 三餐卡：通栏大图 + 餐别语义色徽章 + 信息区 ----
  const MEAL_GRADS: Array<[string, string]> = [
    ['#FFC53D', '#FFA83D'], // 早 · 暖阳
    ['#FF9D82', '#FF6B5B'], // 午 · 珊瑚
    ['#E8652B', '#C94418'], // 晚 · 深珊瑚
  ]
  let cardY = headerH + nutritionH + mealGap
  for (let i = 0; i < data.meals.length; i++) {
    const meal = data.meals[i]
    // 卡体（圆角裁剪，让大图顶部贴卡圆角）
    ctx.save()
    ctx.fillStyle = DMP.surface
    roundRect(ctx, PAD, cardY, W - PAD * 2, mealH, 36)
    ctx.fill()
    roundRect(ctx, PAD, cardY, W - PAD * 2, mealH, 36)
    ctx.clip()

    // 通栏大图
    let drawn = false
    if (meal.image) {
      try {
        drawCover(ctx, await loadCanvasImage(canvas, meal.image), PAD, cardY, W - PAD * 2, mealMediaH, 0)
        drawn = true
      }
      catch { drawn = false }
    }
    if (!drawn) {
      ctx.fillStyle = DMP.peach
      ctx.fillRect(PAD, cardY, W - PAD * 2, mealMediaH)
      centerText(ctx, W / 2, cardY + mealMediaH / 2 - 24, '锅仔', 32, DMP.sub, 'bold')
      centerText(ctx, W / 2, cardY + mealMediaH / 2 + 18, '用心搭', 22, DMP.sub)
    }
    // 图底渐变 scrim，保徽章可读
    const scrim = ctx.createLinearGradient(0, cardY, 0, cardY + mealMediaH)
    scrim.addColorStop(0, 'rgba(30, 16, 8, .20)')
    scrim.addColorStop(0.5, 'rgba(30, 16, 8, 0)')
    scrim.addColorStop(1, 'rgba(30, 16, 8, .30)')
    ctx.fillStyle = scrim
    ctx.fillRect(PAD, cardY, W - PAD * 2, mealMediaH)

    // 餐别徽章（语义渐变）
    const [g0, g1] = MEAL_GRADS[i] || MEAL_GRADS[1]
    const badgeGrad = ctx.createLinearGradient(PAD + 24, cardY + 24, PAD + 160, cardY + 24)
    badgeGrad.addColorStop(0, g0)
    badgeGrad.addColorStop(1, g1)
    ctx.fillStyle = badgeGrad
    roundRect(ctx, PAD + 24, cardY + 24, 132, 52, 26)
    ctx.fill()
    ctx.strokeStyle = 'rgba(255, 255, 255, .4)'
    ctx.lineWidth = 2
    roundRect(ctx, PAD + 24, cardY + 24, 132, 52, 26)
    ctx.stroke()
    centerText(ctx, PAD + 24 + 66, cardY + 37, meal.mealLabel, 24, DMP.white, 'bold')
    ctx.restore()

    // 信息区
    const bodyY = cardY + mealMediaH
    ctx.font = 'bold 36px sans-serif'; ctx.textAlign = 'left'; ctx.textBaseline = 'top'
    ctx.fillStyle = DMP.text
    ctx.fillText(String(meal.name || '').slice(0, 14), PAD + 30, bodyY + 26)

    // meta chips：时长 / 难度
    const metas: string[] = []
    if (meal.cookingTime) metas.push(`${meal.cookingTime} 分钟`)
    if (meal.difficulty) metas.push(meal.difficulty)
    let metaX = PAD + 30
    metas.forEach((m) => {
      ctx.font = '20px sans-serif'
      const w = ctx.measureText(m).width + 36
      ctx.fillStyle = DMP.peach
      roundRect(ctx, metaX, bodyY + 84, w, 42, 21)
      ctx.fill()
      ctx.fillStyle = DMP.body
      ctx.textAlign = 'left'; ctx.textBaseline = 'middle'
      ctx.fillText(m, metaX + 18, bodyY + 106)
      ctx.textBaseline = 'top'
      metaX += w + 12
    })

    // 依据：徽章 + 标题一行，内容一行
    const g = meal.guideline
    if (g && (g.title || g.content)) {
      ctx.fillStyle = DMP.coral
      roundRect(ctx, PAD + 30, bodyY + 142, 104, 38, 19)
      ctx.fill()
      centerText(ctx, PAD + 30 + 52, bodyY + 150, '有据可依', 19, DMP.white, 'bold')
      ctx.font = 'bold 21px sans-serif'; ctx.textAlign = 'left'; ctx.textBaseline = 'top'
      ctx.fillStyle = DMP.accent
      ctx.fillText(String(g.title || '膳食指南建议').slice(0, 18), PAD + 148, bodyY + 151)
      if (g.content) {
        ctx.font = '20px sans-serif'
        ctx.fillStyle = DMP.body
        const line = wrapText(ctx, g.content, W - PAD * 2 - 60, 1)[0] || ''
        ctx.fillText(line, PAD + 30, bodyY + 188 - 4)
      }
    }

    cardY += mealH + mealGap
  }

  // ---- 页脚：品牌 + 口号 + 小程序码 ----
  const footerY = cardY + 4
  drawRule(ctx, footerY)
  leftText(ctx, PAD, footerY + 36, 'GUOZAI · MOOD RECIPE', 18, DMP.accent, 'bold')
  leftText(ctx, PAD, footerY + 70, '依据《中国居民膳食指南（2022）》', 28, DMP.text, 'bold')
  leftText(ctx, PAD, footerY + 116, '扫码让锅仔替你决定今天吃什么', 20, DMP.sub)
  const codeBox = 152
  const codeX = W - PAD - codeBox
  const codeY = footerY + 28
  ctx.fillStyle = DMP.surface
  roundRect(ctx, codeX, codeY, codeBox, codeBox, 18)
  ctx.fill()
  ctx.strokeStyle = DMP.border
  ctx.lineWidth = 2
  roundRect(ctx, codeX, codeY, codeBox, codeBox, 18)
  ctx.stroke()
  await drawPosterCode(ctx, canvas, await ensureWxacodeUrl('daily_menu'), codeX, codeY, codeBox, 14)

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
  await drawPosterCode(ctx, canvas, await ensureWxacodeUrl('record'), codeX, codeY, codeBox)

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
