# -*- coding: utf-8 -*-
import os
d = r"C:/Users/DELL/DoubaoWork/chats/2026-09-01/new-chat/IP视觉资产/表情包/动态GIF专属"
gifs = sorted([f for f in os.listdir(d) if f.endswith(".gif")])

DESC = {
    "A01_害羞捂脸": "害羞摇摆 · 爱心飘起",
    "A02_托腮思考": "左右摇摆 · 思考气泡",
    "A03_惊讶瞪眼": "惊吓抖动 · 冒汗珠",
    "A04_生气嘟嘴": "弹跳 · 头顶冒气",
    "A05_大哭流泪": "抽泣抖动 · 泪滴滑落",
    "A06_飞吻亲亲": "前倾 · 爱心飞出",
    "A07_得意挑眉": "得意摇摆 · 闪光",
    "A08_打哈欠困": "犯困下沉 · Zzz",
    "A09_戴墨镜酷": "酷酷上浮 · 闪光",
    "B01_星星眼馋": "呼吸 · 眼冒星星",
    "B02_拜托求求": "恳求摇摆 · 泪光",
    "B03_干饭真香": "弹跳 · 冒热气",
    "B04_胜利V": "开心弹跳 · 金星迸发",
    "B05_求抱抱": "撒娇摇摆 · 爱心脉冲",
    "B06_累倒瘫坐": "瘫坐下沉 · 冒汗",
    "B07_叉腰理直": "理直摇摆 · 小火焰",
    "B08_嗯嗯点头": "点头确认",
    "B09_疯狂安利": "疯狂抖动 · 星星迸发",
    "C01_头顶问号": "呼吸 · 问号脉冲",
    "C02_闭眼装睡": "平稳呼吸 · 慢Zzz",
    "C03_比OK": "弹跳 · 光环",
    "C04_转圈撒星": "旋转 · 星星环绕",
    "C06_发射爱心": "前倾 · 爱心射出",
    "C08_扭动跳舞": "摇摆旋转 · 音符",
}

css = """
*{margin:0;padding:0;box-sizing:border-box}
body{background:#FDF6F0;font-family:"PingFang SC","Microsoft YaHei",sans-serif;color:#5A3E2B;padding:40px 20px}
.header{text-align:center;margin-bottom:40px}
.header h1{font-size:32px;color:#5A3E2B;margin-bottom:8px}
.header p{color:#8B6B55;font-size:15px}
.section-title{font-size:20px;color:#E8836B;margin:36px 0 16px;padding-left:12px;border-left:4px solid #FFB88C}
.grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(180px,1fr));gap:20px}
.card{background:#FDE6D4;border-radius:12px;overflow:hidden;box-shadow:0 2px 12px rgba(232,131,107,0.1);transition:transform .2s;cursor:pointer}
.card:hover{transform:translateY(-3px)}
.card img{width:100%;display:block;aspect-ratio:1/1;object-fit:cover}
.card-label{padding:8px 10px;font-size:12px;color:#5A3E2B;text-align:center;font-weight:600}
.card-sub{padding:0 10px 10px;font-size:11px;color:#8B6B55;text-align:center}
.modal{display:none;position:fixed;top:0;left:0;width:100%;height:100%;background:rgba(90,62,43,0.9);z-index:1000;justify-content:center;align-items:center;padding:20px}
.modal.active{display:flex}
.modal img{max-height:80vh;max-width:80vw;border-radius:12px;background:#fff}
.modal-close{position:absolute;top:20px;right:28px;color:#fff;font-size:32px;cursor:pointer;background:none;border:none}
.footer{text-align:center;margin-top:48px;color:#8B6B55;font-size:13px}
"""

js = """
function openModal(s){document.getElementById("modalImg").src=s;document.getElementById("modal").classList.add("active")}
function closeModal(){document.getElementById("modal").classList.remove("active")}
document.addEventListener("keydown",function(e){if(e.key==="Escape")closeModal()})
"""

rows = ""
for s in gifs:
    key = s.replace(".gif", "")
    desc = DESC.get(key, "")
    rows += ('<div class="card" onclick="openModal(\'{0}\')"><img src="{0}">'
             '<div class="card-label">{1}</div><div class="card-sub">{2}</div></div>\n').format(s, key, desc)

html = (
    '<!DOCTYPE html>\n<html lang="zh-CN">\n<head>\n<meta charset="UTF-8">\n'
    '<meta name="viewport" content="width=device-width, initial-scale=1.0">\n'
    '<title>锅仔专属动态表情包</title>\n<style>' + css + '</style>\n</head>\n<body>\n'
    '<div class="header"><h1>🍲 锅仔专属动态表情包（24 张 GIF）</h1>'
    '<p>每个表情独立设计动效 · 240×240 · 14帧循环 · 统一 IP 风格</p></div>\n'
    '<div class="section-title">专属动态表情</div>\n<div class="grid">' + rows + '</div>\n'
    '<div class="footer">锅仔专属动态表情包 · 点击任意 GIF 播放动画</div>\n'
    '<div class="modal" id="modal" onclick="closeModal()"><button class="modal-close" onclick="closeModal()">&times;</button><img id="modalImg" src=""></div>\n'
    '<script>' + js + '</script>\n</body>\n</html>\n'
)

with open(os.path.join(d, "专属动态表情预览.html"), "w", encoding="utf-8") as f:
    f.write(html)
print("preview done, gifs:", len(gifs))
