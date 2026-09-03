# -*- coding: utf-8 -*-
import os
from PIL import Image

base = r"C:/Users/DELL/DoubaoWork/chats/2026-09-01/new-chat/IP视觉资产/表情包"
single_dir = os.path.join(base, "单张表情")
wechat = os.path.join(base, "微信上架")
os.makedirs(os.path.join(wechat, "主图240"), exist_ok=True)
os.makedirs(os.path.join(wechat, "缩略图120"), exist_ok=True)

# 从27个中挑选24个：去掉 C05托腮发呆、C07举手抢答、C09瑟瑟发抖
drop = {"C05_托腮发呆.png", "C07_举手抢答.png", "C09_瑟瑟发抖.png"}
chosen = [f for f in sorted(os.listdir(single_dir)) if f not in drop]
print("chosen:", len(chosen))
for f in chosen:
    im = Image.open(os.path.join(single_dir, f)).convert("RGB")
    im240 = im.resize((240, 240), Image.LANCZOS)
    im240.save(os.path.join(wechat, "主图240", f))
    im120 = im240.resize((120, 120), Image.LANCZOS)
    im120.save(os.path.join(wechat, "缩略图120", f))

# 封面图 240x240 与聊天面板图标 50x50：用 core-ip
core = Image.open(os.path.join(base, "..", "00_锅仔_coreIP.png")).convert("RGB")
core.resize((240, 240), Image.LANCZOS).save(os.path.join(wechat, "封面图_240.png"))
core.resize((50, 50), Image.LANCZOS).save(os.path.join(wechat, "聊天面板图标_50.png"))

# 生成选中的24个清单
with open(os.path.join(wechat, "选中的24个表情清单.txt"), "w", encoding="utf-8") as f:
    f.write("\n".join(chosen))
print("done")
