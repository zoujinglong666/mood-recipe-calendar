# -*- coding: utf-8 -*-
"""
「心情菜谱日历」智能排版引擎 · 原型（月度画册样张）
- 自动排版：根据图片数量/比例自动选择布局模板
- 治愈系配色：取 PRD 色彩系统
- 生成 5 页竖版画册（3:4）
"""
import os
from PIL import Image, ImageDraw, ImageFilter, ImageFont, ImageOps

# ---------- 路径 ----------
BASE = r'C:\Users\DELL\DoubaoWork\chats\2026-09-01\new-chat\画册智能排版demo'
DISH_DIR = os.path.join(BASE, 'assets', 'dish')
GUOZAI_DIR = r'C:\Users\DELL\DoubaoWork\chats\2026-09-01\new-chat\mood-recipe-calendar\frontend\src\static\guozai'
OUT_DIR = os.path.join(BASE, 'output')
os.makedirs(OUT_DIR, exist_ok=True)

# ---------- 配色（PRD 治愈系暖色盘） ----------
C_BG      = '#FDF6F0'   # 暖米白
C_CARD    = '#FDE6D4'   # 奶油杏
C_BTN     = '#FFB88C'   # 蜜桃
C_ACCENT  = '#E8836B'   # 暖陶红
C_YELLOW  = '#FFD93D'   # 暖阳黄
C_GREEN   = '#6BCB77'   # 草芽绿
C_BLUE    = '#B5D4E8'   # 淡雾蓝
C_TEXT    = '#5A3E2B'   # 暖褐
C_SUB     = '#8B6B55'   # 浅褐
C_BORDER  = '#EDD4C0'   # 淡杏
C_WHITE   = '#FFFFFF'

# ---------- 字体 ----------
def _font_path():
    cands = [
        r'C:\Windows\Fonts\msyhbd.ttc',  # 微软雅黑 Bold
        r'C:\Windows\Fonts\msyh.ttc',    # 微软雅黑
        r'C:\Windows\Fonts\Deng.ttf',    # 等线
    ]
    for c in cands:
        if os.path.exists(c):
            return c
    return None

FP = _font_path()
def F(size, bold=True):
    """获取中文字体；bold 尽量用雅黑粗体"""
    if not FP:
        return ImageFont.load_default()
    try:
        return ImageFont.truetype(FP, size)
    except Exception:
        return ImageFont.load_default()

# ---------- 工具函数 ----------
def rounded(img, radius):
    """圆角矩形裁剪"""
    mask = Image.new('L', img.size, 0)
    d = ImageDraw.Draw(mask)
    d.rounded_rectangle([0, 0, img.size[0]-1, img.size[1]-1], radius=radius, fill=255)
    out = Image.new('RGBA', img.size)
    out.paste(img, (0, 0), mask)
    return out

def fit_cover(img, w, h):
    """等比缩放 + 居中裁剪到目标尺寸（方形）"""
    ratio = max(w / img.width, h / img.height)
    nw, nh = int(img.width * ratio) + 1, int(img.height * ratio) + 1
    img = img.resize((nw, nh), Image.LANCZOS)
    x = (nw - w) // 2
    y = (nh - h) // 2
    return img.crop((x, y, x + w, y + h))

def load_dishes():
    files = sorted(f for f in os.listdir(DISH_DIR) if f.lower().endswith('.jpg'))
    out = []
    for f in files:
        img = Image.open(os.path.join(DISH_DIR, f)).convert('RGB')
        out.append(img)
    return out

def load_guozai(name, size):
    p = os.path.join(GUOZAI_DIR, name)
    if not os.path.exists(p):
        return None
    img = Image.open(p).convert('RGBA')
    img.thumbnail((size, size), Image.LANCZOS)
    return img

def text_center(d, cx, y, s, font, fill):
    w = d.textlength(s, font=font)
    d.text((cx - w/2, y), s, font=font, fill=fill)

def text_right(d, rx, y, s, font, fill):
    w = d.textlength(s, font=font)
    d.text((rx - w, y), s, font=font, fill=fill)

def paste_rounded(base, img, box, radius=28):
    """把 img（可 RGBA）按 box (x,y,w,h) 圆角贴到底图"""
    x, y, w, h = box
    tmp = fit_cover(img.convert('RGB'), w, h) if img.mode != 'RGBA' or img.has_transparency_data else img
    if tmp.size != (w, h):
        tmp = tmp.resize((w, h), Image.LANCZOS)
    tmp = rounded(tmp.convert('RGBA'), radius)
    base.paste(tmp, (x, y), tmp)

# ---------- 智能排版核心：根据图片数量自动选布局 ----------
def auto_layout(images, page_w, page_h, area):
    """
    根据图片数量返回一组绘制盒 (x, y, w, h)。
    area = (ax, ay, aw, ah) 画册内容区
    规则：
      1 张 -> 居中大图
      2 张 -> 左右对半
      3 张 -> 一大两小（左大右小）
      4 张 -> 2x2
      5 张 -> 一大四小
      6 张 -> 3x2 或 杂志风（本次用：上 1 大 + 下 2x2 + 右侧？）简化：2 行 3 列
    """
    ax, ay, aw, ah = area
    n = len(images)
    gap = 24
    boxes = []
    if n == 1:
        boxes = [(ax, ay, aw, ah)]
    elif n == 2:
        w = (aw - gap) // 2
        boxes = [(ax, ay, w, ah), (ax + w + gap, ay, w, ah)]
    elif n == 3:
        # 左大右小（右侧两行）
        wL = int(aw * 0.58)
        wR = aw - wL - gap
        hR = (ah - gap) // 2
        boxes = [(ax, ay, wL, ah),
                 (ax + wL + gap, ay, wR, hR),
                 (ax + wL + gap, ay + hR + gap, wR, hR)]
    elif n == 4:
        w = (aw - gap) // 2
        h = (ah - gap) // 2
        boxes = [(ax, ay, w, h), (ax + w + gap, ay, w, h),
                 (ax, ay + h + gap, w, h), (ax + w + gap, ay + h + gap, w, h)]
    elif n == 5:
        wL = int(aw * 0.58)
        wR = aw - wL - gap
        hR = (ah - gap) // 2
        boxes = [(ax, ay, wL, ah),
                 (ax + wL + gap, ay, wR, hR),
                 (ax + wL + gap, ay + hR + gap, wR, hR),
                 (ax, ay + ah + 8, 1, 1)]  # 兜底，实际不会到这里
    else:
        # n>=6：上 1 大图 + 下 2x2（杂志风）
        big_h = int(ah * 0.52)
        small_h = (ah - big_h - gap) // 2
        small_w = (aw - gap) // 2
        boxes = [(ax, ay, aw, big_h),
                 (ax, ay + big_h + gap, small_w, small_h),
                 (ax + small_w + gap, ay + big_h + gap, small_w, small_h),
                 (ax, ay + big_h + gap + small_h + gap, small_w, small_h),
                 (ax + small_w + gap, ay + big_h + gap + small_h + gap, small_w, small_h)]
    # 若数量超出模板容量，把多余图等比缩小挤进去（引擎可扩展性）
    if n > len(boxes):
        boxes = boxes[:n]
    return boxes[:n]

# ---------- 各页绘制 ----------
def draw_cover(dish, guozai):
    """第1页 封面：模糊背景 + 标题 + 锅仔抱相册"""
    W, H = 1080, 1440
    bg = dish.convert('RGB').resize((W, H), Image.LANCZOS).filter(ImageFilter.GaussianBlur(14))
    overlay = Image.new('RGBA', (W, H), (253, 246, 240, 205))
    base = Image.alpha_composite(bg.convert('RGBA'), overlay).convert('RGB')
    d = ImageDraw.Draw(base)

    # 装饰圆点
    d.ellipse([80, 110, 130, 160], fill=C_YELLOW)
    d.ellipse([W-160, 170, W-105, 225], fill=C_ACCENT)
    d.ellipse([150, H-220, 185, H-185], fill=C_BLUE)

    # 顶部月份标签
    tag_w, tag_h = 260, 64
    d.rounded_rectangle([W/2-tag_w/2, 300, W/2+tag_w/2, 300+tag_h], radius=32, fill=(255, 255, 255, 210))
    text_center(d, W/2, 316, '2026.08', F(30), C_ACCENT)

    # 大标题
    title = '小圆同学的'
    title2 = '8月干饭日记'
    text_center(d, W/2, 430, title, F(78, bold=True), C_TEXT)
    text_center(d, W/2, 540, title2, F(78, bold=True), C_TEXT)

    # slogan
    text_center(d, W/2, 660, '用一道菜，治愈今天的你', F(34), C_SUB)

    # 锅仔（抱相册）居中偏下
    if guozai:
        gx = int((W - guozai.width) / 2)
        base.paste(guozai, (gx, H - guozai.height - 130), guozai)

    # 底部小字
    text_center(d, W/2, H-70, '你的情绪味蕾搭子「锅仔」', F(26), C_SUB)
    return base

def draw_overview(dishes, guozai):
    """第2页 月度盘点"""
    W, H = 1080, 1440
    base = Image.new('RGB', (W, H), C_BG)
    d = ImageDraw.Draw(base)

    # 标题
    d.rounded_rectangle([60, 64, 250, 132], radius=34, fill=C_CARD)
    text_center(d, 155, 82, '月度盘点', F(38, bold=True), C_TEXT)

    # 统计卡片（3 个）
    stats = [('记录了', '23', '天'), ('连续打卡', '7', '天 🔥'), ('本月新菜', '12', '道')]
    card_w = 300
    gap = 24
    total = card_w * 3 + gap * 2
    x0 = (W - total) // 2
    y0 = 190
    for i, (lab, num, unit) in enumerate(stats):
        x = x0 + i * (card_w + gap)
        d.rounded_rectangle([x, y0, x+card_w, y0+180], radius=28, fill=C_CARD)
        text_center(d, x+card_w/2, y0+36, lab, F(26), C_SUB)
        nw = d.textlength(num, font=F(64, bold=True))
        d.text((x+card_w/2 - nw/2, y0+82), num, font=F(64, bold=True), fill=C_ACCENT)
        text_center(d, x+card_w/2, y0+150, unit, F(24), C_SUB)

    # 心情分布
    d.rounded_rectangle([80, 440, W-80, 700], radius=32, fill=C_WHITE, outline=C_BORDER, width=2)
    text_center(d, W/2, 472, '心情分布', F(34, bold=True), C_TEXT)
    moods = [('开心', 8, C_YELLOW), ('平静', 6, C_GREEN), ('疲惫', 5, C_BLUE), ('难过', 4, C_ACCENT)]
    mx0, mx1, my = 150, W-150, 545
    total_m = sum(m[1] for m in moods)
    seg_w = mx1 - mx0
    for i, (name, cnt, col) in enumerate(moods):
        w = seg_w * cnt / total_m
        d.rounded_rectangle([mx0, my, mx0 + w, my + 36], radius=18, fill=col)
        if w > 80:
            text_center(d, mx0 + w/2, my + 6, f'{name} {cnt}', F(22, bold=True), C_TEXT if col != C_YELLOW else C_TEXT)
        mx0 += w
    # 图例
    lx = 150
    for name, cnt, col in moods:
        d.rounded_rectangle([lx, 770, lx+26, 796], radius=8, fill=col)
        d.text((lx+38, 764), f'{name} {cnt}天', font=F(24), fill=C_SUB)
        lx += d.textlength(f'{name} {cnt}天', font=F(24)) + 110

    # Top3 菜品
    d.rounded_rectangle([80, 850, W-80, 1180], radius=32, fill=C_WHITE, outline=C_BORDER, width=2)
    text_center(d, W/2, 882, '本月最常做的菜', F(34, bold=True), C_TEXT)
    tops = [('🥇', '红烧肉', '4 次'), ('🥈', '番茄炒蛋', '3 次'), ('🥉', '葱油拌面', '2 次')]
    for i, (medal, name, cnt) in enumerate(tops):
        y = 950 + i * 70
        d.text((150, y), medal, font=F(36))
        d.text((230, y), name, font=F(32), fill=C_TEXT)
        text_right(d, W-150, y, cnt, font=F(28), fill=C_SUB)

    # 锅仔趴角落
    if guozai:
        base.paste(guozai, (W - guozai.width - 20, H - guozai.height - 60), guozai)
    return base

def draw_records(dishes, guozai, labels):
    """第3页 每日记录：多图智能排版"""
    W, H = 1080, 1440
    base = Image.new('RGB', (W, H), C_BG)
    d = ImageDraw.Draw(base)

    d.rounded_rectangle([60, 64, 250, 132], radius=34, fill=C_CARD)
    text_center(d, 155, 82, '干饭日记', F(38, bold=True), C_TEXT)
    d.text((280, 90), '每天都有好好吃饭', font=F(26), fill=C_SUB)

    # 智能排版区域
    area = (80, 190, W-160, 1080)
    boxes = auto_layout(dishes, W, H, area)

    for i, box in enumerate(boxes):
        x, y, w, h = box
        # 白边卡片
        d.rounded_rectangle([x-8, y-8, x+w+8, y+h+8], radius=32, fill=C_WHITE, outline=C_BORDER, width=2)
        # 图片
        paste_rounded(base, dishes[i], (x, y, w, h), radius=24)
        # 底部标签条（文字自适应字号并居中，避免截断）
        label = labels[i % len(labels)]
        fs = 22 if len(label) > 4 else 26
        d.rounded_rectangle([x, y+h-58, x+w, y+h], radius=22, fill=(90, 62, 43))
        lw = d.textlength(label, font=F(fs, bold=True))
        d.text((x + w/2 - lw/2, y+h-40), label, font=F(fs, bold=True), fill=C_WHITE)

    # 锅仔：缩小后放顶部标题旁，不遮挡菜品
    if guozai:
        g = guozai.copy()
        g.thumbnail((170, 170), Image.LANCZOS)
        base.paste(g, (290, 40), g)
    text_right(d, W-80, H-70, '还有 12 道菜没放进来…', font=F(26), fill=C_SUB)
    return base

def draw_ai(guozai):
    """第4页 AI 月度寄语"""
    W, H = 1080, 1440
    base = Image.new('RGB', (W, H), C_BG)
    d = ImageDraw.Draw(base)
    # 顶部渐变装饰
    grad = Image.new('RGB', (W, 340))
    for y in range(340):
        t = y / 340
        r = int(253 + (232 - 253) * t)
        g = int(246 + (230 - 246) * t)
        b = int(240 + (212 - 240) * t)
        for x in range(W):
            grad.putpixel((x, y), (r, g, b))
    base.paste(grad, (0, 0))

    # 星星装饰
    d.ellipse([90, 120, 150, 180], fill=C_YELLOW)
    d.ellipse([W-170, 200, W-100, 270], fill=C_ACCENT)

    text_center(d, W/2, 90, '锅仔说', F(56, bold=True), C_TEXT)
    text_center(d, W/2, 170, '—— 月度寄语 ——', F(28), C_SUB)

    # 寄语卡片
    card = [90, 320, W-90, 1080]
    d.rounded_rectangle(card, radius=36, fill=C_CARD)
    lines = [
        '小圆，这个月你记录了 23 天，',
        '有 12 天选择了「治愈」系食物。',
        '',
        '看来 8 月工作压力不小，',
        '但你没有饿着自己——',
        '你煮了汤、炖了肉、炒了饭。',
        '',
        '每一顿热饭，都是你在好好爱自己。',
        '',
        '9 月，请继续对自己好一点。',
    ]
    y = 400
    for line in lines:
        if line == '':
            y += 34
            continue
        d.text((160, y), line, font=F(38), fill=C_TEXT)
        y += 68

    # 锅仔开心
    if guozai:
        gx = int((W - guozai.width) / 2)
        base.paste(guozai, (gx, H - guozai.height - 120), guozai)

    text_center(d, W/2, H-60, '今天也好好吃饭了吗？', F(28), C_SUB)
    return base

def draw_share(dishes, guozai):
    """第5页 分享页"""
    W, H = 1080, 1440
    base = Image.new('RGB', (W, H), C_BG)
    d = ImageDraw.Draw(base)

    d.rounded_rectangle([60, 64, 250, 132], radius=34, fill=C_CARD)
    text_center(d, 155, 82, '8月汇总', F(38, bold=True), C_TEXT)

    # 4 张小图预览
    area = (80, 190, W-160, 560)
    small = dishes[:4]
    boxes = auto_layout(small, W, H, area)
    for i, box in enumerate(boxes):
        x, y, w, h = box
        paste_rounded(base, small[i], (x, y, w, h), radius=24)

    # 数据条
    d.rounded_rectangle([80, 800, W-80, 1010], radius=32, fill=C_WHITE, outline=C_BORDER, width=2)
    text_center(d, W/2, 840, '本月我做了 23 顿好饭', F(38, bold=True), C_TEXT)
    text_center(d, W/2, 920, '最常做：红烧肉 · 连续打卡 7 天', F(28), C_SUB)

    # 分享按钮
    btn_w, btn_h = 420, 96
    d.rounded_rectangle([W/2-btn_w/2, 1060, W/2+btn_w/2, 1060+btn_h], radius=48, fill=C_BTN)
    text_center(d, W/2, 1086, '分享我的 8 月干饭日记', F(32, bold=True), C_WHITE)

    # 锅仔
    if guozai:
        gx = int((W - guozai.width) / 2)
        base.paste(guozai, (gx, H - guozai.height - 100), guozai)

    text_center(d, W/2, H-60, '扫码看我的心情菜谱日历', F(26), C_SUB)
    return base

# ---------- 主流程 ----------
def main():
    dishes = load_dishes()
    if not dishes:
        print('No dishes')
        return
    print(f'加载 {len(dishes)} 张菜品图')
    guozai_album = load_guozai('action_05_album.png', 420)
    guozai_happy = load_guozai('mood_01_happy.png', 320)
    guozai_bowl  = load_guozai('action_01_bowl.png', 280)

    labels = ['番茄牛腩汤', '红烧肉', '番茄炒蛋', '葱油拌面', '南瓜粥', '清炒时蔬']

    pages = [
        ('01_封面.png',  draw_cover(dishes[0], guozai_album)),
        ('02_月度盘点.png', draw_overview(dishes, guozai_bowl)),
        ('03_每日记录.png', draw_records(dishes, guozai_bowl, labels)),
        ('04_月度寄语.png', draw_ai(guozai_happy)),
        ('05_分享页.png',  draw_share(dishes, guozai_bowl)),
    ]
    for name, img in pages:
        img.save(os.path.join(OUT_DIR, name), quality=92)
        print('saved', name, img.size)

    print('完成 ->', OUT_DIR)

if __name__ == '__main__':
    main()
