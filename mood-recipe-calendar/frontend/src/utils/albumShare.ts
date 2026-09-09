/**
 * 分享长图导出（Canvas 2D）
 * 把画册分享页绘制成竖版长图（9:16 比例），保存到相册。
 * 兼容微信小程序 Canvas 2D（type="2d"）。
 */
import { getCurrentInstance } from 'vue'

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
}

export interface RecipeShareData {
  name: string
  mood: string
  reason: string
  cookingTime?: number
  difficulty?: string
  ingredients?: string[]
  image?: string
  guozaiPath: string
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
  const dpr = (uni.getSystemInfoSync().pixelRatio || 2)
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

/** 将当前一条推荐画成可保存的食谱卡；图片资源失败时仍输出文字卡。 */
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
  const H = 1120
  const canvas = canvasInfo.node
  const ctx = canvas.getContext('2d')
  const dpr = uni.getSystemInfoSync().pixelRatio || 2
  canvas.width = W * dpr
  canvas.height = H * dpr
  ctx.scale(dpr, dpr)

  ctx.fillStyle = COLORS.bg
  ctx.fillRect(0, 0, W, H)
  ctx.fillStyle = COLORS.yellow
  ctx.beginPath()
  ctx.arc(70, 86, 28, 0, Math.PI * 2)
  ctx.fill()
  ctx.fillStyle = COLORS.blue
  ctx.beginPath()
  ctx.arc(W - 72, 148, 36, 0, Math.PI * 2)
  ctx.fill()
  centerText(ctx, W / 2, 54, '锅仔 · 今日食谱', 26, COLORS.sub, 'bold')

  const heroX = 44
  const heroY = 118
  const heroW = W - 88
  const heroH = 340
  ctx.fillStyle = COLORS.card
  roundRect(ctx, heroX, heroY, heroW, heroH, 38)
  ctx.fill()
  if (data.image) {
    try {
      drawCover(ctx, await loadCanvasImage(canvas, data.image), heroX, heroY, heroW, heroH, 38)
    }
    catch {
      /* 菜图不可用时保留柔和的纯色主视觉。 */
    }
  }
  ctx.fillStyle = 'rgba(90, 62, 43, .22)'
  roundRect(ctx, heroX, heroY + heroH - 112, heroW, 112, 0)
  ctx.fill()
  ctx.fillStyle = 'rgba(255,255,255,.92)'
  roundRect(ctx, heroX + 26, heroY + 24, 136, 54, 27)
  ctx.fill()
  centerText(ctx, heroX + 94, heroY + 39, `今日 · ${data.mood}`, 22, COLORS.text, 'bold')
  centerText(ctx, W / 2, heroY + heroH - 82, data.name.slice(0, 14), 46, COLORS.white, 'bold')

  const meta = `${data.cookingTime || '--'} 分钟  ·  ${data.difficulty || '家常难度'}`
  centerText(ctx, W / 2, 500, meta, 26, COLORS.sub, 'bold')

  ctx.fillStyle = COLORS.white
  roundRect(ctx, 44, 550, W - 88, 216, 32)
  ctx.fill()
  try {
    const guozai = await loadCanvasImage(canvas, data.guozaiPath)
    ctx.drawImage(guozai, 68, 578, 118, 118)
  }
  catch { /* 锅仔资源失败不影响推荐理由。 */ }
  ctx.fillStyle = COLORS.accent
  roundRect(ctx, 182, 584, 168, 42, 21)
  ctx.fill()
  centerText(ctx, 266, 594, '锅仔说', 20, COLORS.white, 'bold')
  ctx.fillStyle = COLORS.text
  ctx.font = '28px sans-serif'
  ctx.textAlign = 'left'
  ctx.textBaseline = 'top'
  wrapText(ctx, data.reason, 480, 3).forEach((line, index) => ctx.fillText(line, 206, 644 + index * 39))

  ctx.fillStyle = '#F9EBDD'
  roundRect(ctx, 44, 802, W - 88, 154, 32)
  ctx.fill()
  ctx.fillStyle = COLORS.text
  ctx.font = 'bold 25px sans-serif'
  ctx.textAlign = 'left'
  ctx.fillText('准备这些就可以开做', 78, 834)
  ctx.font = '24px sans-serif'
  const ingredientText = (data.ingredients || []).slice(0, 4).join('  ·  ') || '跟着锅仔慢慢做一顿热饭'
  wrapText(ctx, ingredientText, W - 154, 2).forEach((line, index) => ctx.fillText(line, 78, 880 + index * 34))

  centerText(ctx, W / 2, 1012, '心情菜谱日历', 30, COLORS.text, 'bold')
  centerText(ctx, W / 2, 1062, '用一道菜，治愈今天的你', 24, COLORS.sub)

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
