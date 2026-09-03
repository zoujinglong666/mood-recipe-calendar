# -*- coding: utf-8 -*-
"""锅仔表情包 - 逐表情专属动画引擎（PIL 逐帧合成 GIF）"""
import os, math
from PIL import Image, ImageDraw, ImageFont

BASE = r"C:/Users/DELL/DoubaoWork/chats/2026-09-01/new-chat/IP视觉资产/表情包"
SINGLE = os.path.join(BASE, "单张表情")
OUT = os.path.join(BASE, "动态GIF专属")
os.makedirs(OUT, exist_ok=True)

N = 14
DUR = 75
SIZE = 240
S0 = 196  # 基准表情尺寸

# 颜色
WHITE  = (255, 255, 255)
YELLOW = (255, 217, 61)
ORANGE = (255, 184, 140)
RED    = (232, 131, 107)
PINK   = (255, 150, 160)
BLUE   = (181, 212, 232)
BLUE2  = (140, 180, 220)
LIGHT  = (255, 240, 210)
GREY   = (170, 170, 180)
STEAM  = (250, 250, 250)

ARIALBD = "C:/Windows/Fonts/arialbd.ttf"
SEGUEMJ = "C:/Windows/Fonts/seguiemj.ttf"
PI2 = 2 * math.pi

# ---------------- 基础绘图函数 ----------------
def draw_star(d, cx, cy, r, color):
    pts = []
    for i in range(10):
        ang = -math.pi / 2 + i * math.pi / 5
        rr = r if i % 2 == 0 else r * 0.45
        pts.append((cx + rr * math.cos(ang), cy + rr * math.sin(ang)))
    d.polygon(pts, fill=color)

def draw_heart(d, cx, cy, r, color):
    d.ellipse([cx - 2 * r, cy - r, cx, cy + r], fill=color)
    d.ellipse([cx, cy - r, cx + 2 * r, cy + r], fill=color)
    d.polygon([(cx - 2 * r, cy), (cx + 2 * r, cy), (cx, cy + 2 * r)], fill=color)

def draw_drop(d, cx, cy, r, color):
    d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=color)
    d.polygon([(cx - r, cy), (cx + r, cy), (cx, cy - r * 1.7)], fill=color)

def draw_sparkle(d, cx, cy, r, color):
    d.polygon([(cx, cy - r), (cx + r*0.3, cy - r*0.3), (cx + r, cy), (cx + r*0.3, cy + r*0.3),
               (cx, cy + r), (cx - r*0.3, cy + r*0.3), (cx - r, cy), (cx - r*0.3, cy - r*0.3)], fill=color)

# ---------------- 动作计算 ----------------
def action_transform(action, ph):
    s, dx, dy, rot = 1.0, 0, 0, 0
    if action == "breathe":
        s = 1 + 0.05 * math.sin(ph)
    elif action == "bounce":
        dy = -14 * max(0.0, math.sin(ph))
    elif action == "sway":
        dx = 10 * math.sin(ph); rot = 3 * math.sin(ph)
    elif action == "nod":
        dy = 8 * math.sin(2 * ph)
    elif action == "shake":
        dx = 7 * math.sin(3 * ph); dy = 5 * math.sin(5 * ph); rot = 2 * math.sin(4 * ph)
    elif action == "rotate":
        rot = 14 * math.sin(ph)
    elif action == "sink":
        dy = 12 * (0.5 + 0.5 * math.sin(ph - math.pi / 2))
    elif action == "float_up":
        dy = -10 * (0.5 + 0.5 * math.sin(ph))
    elif action == "lunge":
        dx = 9 * math.sin(ph); rot = 10 * math.sin(ph)
    elif action == "sway_rotate":
        dx = 12 * math.sin(ph); rot = 11 * math.sin(ph)
    return s, dx, dy, rot

# ---------------- 装饰元素 ----------------
def draw_decor(d, decor, i, N, ph):
    t = (i % N) / N
    if decor == "hearts_float":
        for k, (hx, hb) in enumerate([(55, 160), (120, 205), (185, 150)]):
            tt = ((i + k * 5) % N) / N
            yy = hb - tt * 100
            draw_heart(d, hx, yy, 4 + 2 * tt, PINK)
    elif decor == "bubble_think":
        yy = 20 + 3 * math.sin(ph)
        d.ellipse([158, yy, 198, yy + 26], fill=WHITE, outline=(235, 210, 190))
        d.polygon([(172, yy + 26), (168, yy + 34), (182, yy + 26)], fill=WHITE, outline=(235, 210, 190))
        for k in range(3):
            d.ellipse([164 + k * 9, yy + 8 + (k % 2) * 5, 169 + k * 9, yy + 13 + (k % 2) * 5], fill=(200, 170, 150))
    elif decor == "sweat":
        draw_drop(d, 205, 48 + t * 40, 6, BLUE)
    elif decor == "anger_puff":
        yy = 78 - t * 42; rr = 8 + t * 14
        d.ellipse([120 - rr, yy - rr, 120 + rr, yy + rr], fill=(235, 235, 235), outline=(200, 200, 200))
    elif decor == "tears":
        draw_drop(d, 100, 92 + t * 72, 6, BLUE2)
        draw_drop(d, 143, 104 + t * 60, 5, BLUE2)
    elif decor == "heart_fly":
        hx = 120 + t * 88; hy = 152 - t * 100
        draw_heart(d, hx, hy, 10 * (1 - t) + 2, RED)
    elif decor == "sparkle":
        p = 0.5 + 0.5 * math.sin(ph)
        draw_sparkle(d, 55, 62, 4 + 6 * p, YELLOW)
        draw_sparkle(d, 186, 52, 3 + 5 * (1 - p), ORANGE)
    elif decor == "zzz":
        f1 = ImageFont.truetype(ARIALBD, int(13 + 11 * t))
        f2 = ImageFont.truetype(ARIALBD, int(9 + 7 * t))
        d.text((158, 42 - t * 40), "Z", font=f1, fill=GREY)
        d.text((185, 58 - t * 40), "z", font=f2, fill=(200, 200, 205))
    elif decor == "stars_twinkle":
        p = 0.5 + 0.5 * math.sin(ph)
        draw_star(d, 100, 116, 3.5 + 4 * p, YELLOW)
        draw_star(d, 143, 120, 3.5 + 4 * (1 - p), YELLOW)
    elif decor == "sweat_tear":
        draw_drop(d, 204, 48 + t * 30, 5, BLUE)
        draw_sparkle(d, 34, 70, 3 + 3 * (0.5 + 0.5 * math.sin(ph)), LIGHT)
    elif decor == "steam":
        yy = 175 - t * 70; rr = 5 + t * 11
        d.ellipse([120 - rr, yy - rr, 120 + rr, yy + rr], fill=STEAM, outline=(222, 216, 212))
    elif decor == "star_burst":
        for k in range(5):
            ang = k * 2 * math.pi / 5 + ph
            dist = 22 + t * 48
            sx = 120 + dist * math.cos(ang); sy = 42 + dist * math.sin(ang) * 0.55
            draw_star(d, sx, sy, 5 - t * 2.5, YELLOW)
    elif decor == "heart_pulse":
        p = 0.5 + 0.5 * math.sin(ph)
        draw_heart(d, 120, 158, 7 + 6 * p, RED)
    elif decor == "puff_flame":
        p = 0.5 + 0.5 * math.sin(ph)
        fy = 58 - 6 * p
        d.ellipse([113, fy - 8, 127, fy], fill=(255, 140, 80))
        d.ellipse([116, fy - 15, 124, fy - 3], fill=(255, 190, 90))
    elif decor == "question_pulse":
        p = 0.5 + 0.5 * math.sin(ph)
        f = ImageFont.truetype(ARIALBD, int(16 + 12 * p))
        d.text((108, 14 - 3 * p), "?", font=f, fill=(150, 125, 110))
    elif decor == "zzz_slow":
        tt = ((i * 2) % N) / N
        f = ImageFont.truetype(ARIALBD, int(12 + 10 * tt))
        d.text((162, 40 - tt * 42), "Z", font=f, fill=GREY)
    elif decor == "halo":
        p = 0.5 + 0.5 * math.sin(ph)
        r = 104 + 8 * p
        d.ellipse([120 - r, 120 - r, 120 + r, 120 + r], outline=ORANGE, width=3)
    elif decor == "stars_spin":
        for k in range(6):
            a = ph + k * 2 * math.pi / 6
            sx = 120 + 108 * math.cos(a); sy = 120 + 108 * math.sin(a)
            draw_star(d, sx, sy, 4, YELLOW)
    elif decor == "heart_shoot":
        hx = 55 + t * 155; hy = 155 - 18 * math.sin(ph)
        draw_heart(d, hx, hy, 8, RED)
    elif decor == "notes":
        f = ImageFont.truetype(SEGUEMJ, 20)
        for k, (nx, ny0, dl) in enumerate([(28, 125, 0), (205, 145, 4), (168, 62, 8)]):
            tt = ((i + dl) % N) / N
            d.text((nx, ny0 - tt * 55), "♪", font=f, fill=(232, 131, 107))

# ---------------- 每个表情的专属动画配置 ----------------
ANIMS = {
    "A01_害羞捂脸": ("sway", "hearts_float"),
    "A02_托腮思考": ("sway", "bubble_think"),
    "A03_惊讶瞪眼": ("shake", "sweat"),
    "A04_生气嘟嘴": ("bounce", "anger_puff"),
    "A05_大哭流泪": ("shake", "tears"),
    "A06_飞吻亲亲": ("lunge", "heart_fly"),
    "A07_得意挑眉": ("sway", "sparkle"),
    "A08_打哈欠困": ("sink", "zzz"),
    "A09_戴墨镜酷": ("float_up", "sparkle"),
    "B01_星星眼馋": ("breathe", "stars_twinkle"),
    "B02_拜托求求": ("sway", "sweat_tear"),
    "B03_干饭真香": ("bounce", "steam"),
    "B04_胜利V":    ("bounce", "star_burst"),
    "B05_求抱抱":   ("sway", "heart_pulse"),
    "B06_累倒瘫坐": ("sink", "sweat"),
    "B07_叉腰理直": ("sway", "puff_flame"),
    "B08_嗯嗯点头": ("nod", None),
    "B09_疯狂安利": ("shake", "star_burst"),
    "C01_头顶问号": ("breathe", "question_pulse"),
    "C02_闭眼装睡": ("breathe", "zzz_slow"),
    "C03_比OK":    ("bounce", "halo"),
    "C04_转圈撒星": ("rotate", "stars_spin"),
    "C06_发射爱心": ("lunge", "heart_shoot"),
    "C08_扭动跳舞": ("sway_rotate", "notes"),
}

def make_gif(name, src, action, decor):
    base_img = Image.open(src).convert("RGB")
    frames = []
    for i in range(N):
        ph = PI2 * i / N
        s, dx, dy, rot = action_transform(action, ph)
        ss = max(120, int(S0 * s))
        emoji = base_img.resize((ss, ss), Image.LANCZOS)
        if abs(rot) > 0.2:
            emoji = emoji.rotate(rot, resample=Image.BICUBIC, fillcolor=WHITE)
        canvas = Image.new("RGB", (SIZE, SIZE), WHITE)
        px = (SIZE - ss) // 2 + int(dx)
        py = (SIZE - ss) // 2 + int(dy)
        canvas.paste(emoji, (px, py))
        d = ImageDraw.Draw(canvas)
        if decor:
            draw_decor(d, decor, i, N, ph)
        frames.append(canvas)
    frames[0].save(os.path.join(OUT, name + ".gif"),
                   save_all=True, append_images=frames[1:], duration=DUR, loop=0)

ok = []
for name, (action, decor) in ANIMS.items():
    src = os.path.join(SINGLE, name + ".png")
    if os.path.exists(src):
        make_gif(name, src, action, decor)
        ok.append(name)
print("专属动画生成:", len(ok))
for n in ok:
    print(" -", n)
