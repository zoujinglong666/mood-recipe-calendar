# -*- coding: utf-8 -*-
import os
from PIL import Image, ImageChops

src_dir = r"C:/Users/DELL/DoubaoWork/chats/2026-09-01/new-chat/IP视觉资产/表情包"
out_dir = os.path.join(src_dir, "单张表情")
os.makedirs(out_dir, exist_ok=True)

groups = [
    ("A组_害羞思考惊讶生气大哭飞吻得意哈欠酷.png", ["害羞捂脸","托腮思考","惊讶瞪眼","生气嘟嘴","大哭流泪","飞吻亲亲","得意挑眉","打哈欠困","戴墨镜酷"]),
    ("B组_星星眼拜托干饭胜利抱抱累倒叉腰点头安利.png", ["星星眼馋","拜托求求","干饭真香","胜利V","求抱抱","累倒瘫坐","叉腰理直","嗯嗯点头","疯狂安利"]),
    ("C组_问号装睡OK转圈托腮爱心举手跳舞发抖.png", ["头顶问号","闭眼装睡","比OK","转圈撒星","托腮发呆","发射爱心","举手抢答","扭动跳舞","瑟瑟发抖"]),
]

def content_bbox(img, threshold=30):
    bg = Image.new('RGB', img.size, (255,255,255))
    diff = ImageChops.difference(img, bg)
    g = diff.convert('L')
    return g.point(lambda p: 255 if p > threshold else 0).getbbox()

OUT = 800
count = 0
for letter, (fname, names) in zip('ABC', groups):
    img = Image.open(os.path.join(src_dir, fname)).convert('RGB')
    w, h = img.size
    cw, ch = w // 3, h // 3
    for i in range(3):
        for j in range(3):
            idx = i * 3 + j
            cell = img.crop((j*cw, i*ch, (j+1)*cw, (i+1)*ch))
            bb = content_bbox(cell)
            if bb:
                x0, y0, x1, y1 = bb
                padx = (x1 - x0) * 0.18
                pady = (y1 - y0) * 0.18
                nx0 = max(0, int(x0 - padx)); ny0 = max(0, int(y0 - pady))
                nx1 = min(cell.width,  int(x1 + padx)); ny1 = min(cell.height, int(y1 + pady))
                cell = cell.crop((nx0, ny0, nx1, ny1))
            cell = cell.resize((OUT, OUT), Image.LANCZOS)
            name = f"{letter}{idx+1:02d}_{names[idx]}.png"
            cell.save(os.path.join(out_dir, name))
            count += 1
print("done", count)
