# -*- coding: utf-8 -*-
import os, math
from PIL import Image, ImageDraw

base = r"C:/Users/DELL/DoubaoWork/chats/2026-09-01/new-chat/IP视觉资产/表情包"
single_dir = os.path.join(base, "单张表情")
out_dir = os.path.join(base, "动态GIF")
os.makedirs(out_dir, exist_ok=True)

drop = {"C05_托腮发呆.png", "C07_举手抢答.png", "C09_瑟瑟发抖.png"}
chosen = [f for f in sorted(os.listdir(single_dir)) if f not in drop]

N = 12
DUR = 70
SIZE = 240
YELLOW = (255, 217, 61)
ORANGE = (255, 184, 140)
RED = (232, 131, 107)

def draw_star(draw, cx, cy, r_outer, color):
    pts = []
    for i in range(10):
        angle = -math.pi / 2 + i * math.pi / 5
        r = r_outer if i % 2 == 0 else r_outer * 0.45
        pts.append((cx + r * math.cos(angle), cy + r * math.sin(angle)))
    draw.polygon(pts, fill=color)

def make_gif(src, dst):
    base_img = Image.open(src).convert("RGB")
    frames = []
    for i in range(N):
        phase = 2 * math.pi * i / N
        pulse = 0.5 + 0.5 * math.sin(phase)  # 0~1 循环
        scale = 1 + 0.05 * math.sin(phase)   # 呼吸缩放
        bs = int(232 * scale)
        canvas = Image.new("RGB", (SIZE, SIZE), (255, 255, 255))
        emoji = base_img.resize((bs, bs), Image.LANCZOS)
        x = (SIZE - bs) // 2
        y = (SIZE - bs) // 2 + int(4 * math.sin(phase))
        canvas.paste(emoji, (x, y))
        d = ImageDraw.Draw(canvas)
        # 头顶星星闪烁（固定位置，随脉冲变化大小）
        for sx, sy, base_r, color in [
            (120, 40, 5.5, YELLOW),
            (55, 58, 4.0, ORANGE),
            (186, 52, 4.5, RED),
        ]:
            r = base_r + 3.5 * pulse
            draw_star(d, sx, sy, r, color)
        frames.append(canvas)
    frames[0].save(dst, save_all=True, append_images=frames[1:], duration=DUR, loop=0)

for f in chosen:
    make_gif(os.path.join(single_dir, f), os.path.join(out_dir, f.replace(".png", ".gif")))
print("GIF generated:", len(chosen))
