# -*- coding: utf-8 -*-
import os
d = r"C:/Users/DELL/DoubaoWork/chats/2026-09-01/new-chat/IP视觉资产/表情包/动态GIF"
gifs = sorted([f for f in os.listdir(d) if f.endswith(".gif")])

css = """
*{margin:0;padding:0;box-sizing:border-box}
body{background:#FDF6F0;font-family:"PingFang SC","Microsoft YaHei",sans-serif;color:#5A3E2B;padding:40px 20px}
.header{text-align:center;margin-bottom:40px}
.header h1{font-size:32px;color:#5A3E2B;margin-bottom:8px}
.header p{color:#8B6B55;font-size:15px}
.section-title{font-size:20px;color:#E8836B;margin:36px 0 16px;padding-left:12px;border-left:4px solid #FFB88C}
.grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(160px,1fr));gap:20px}
.card{background:#FDE6D4;border-radius:12px;overflow:hidden;box-shadow:0 2px 12px rgba(232,131,107,0.1);transition:transform .2s;cursor:pointer}
.card:hover{transform:translateY(-3px)}
.card img{width:100%;display:block;aspect-ratio:1/1;object-fit:cover}
.card-label{padding:8px;font-size:12px;color:#5A3E2B;text-align:center;font-weight:500}
.modal{display:none;position:fixed;top:0;left:0;width:100%;height:100%;background:rgba(90,62,43,0.9);z-index:1000;justify-content:center;align-items:center;padding:20px}
.modal.active{display:flex}
.modal img{max-height:80vh;max-width:80vw;border-radius:12px;background:#fff;image-rendering:pixelated}
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
    rows += '<div class="card" onclick="openModal(\'{0}\')"><img src="{0}"><div class="card-label">{1}</div></div>\n'.format(s, s.replace(".gif", ""))

html = (
    '<!DOCTYPE html>\n<html lang="zh-CN">\n<head>\n<meta charset="UTF-8">\n'
    '<meta name="viewport" content="width=device-width, initial-scale=1.0">\n'
    '<title>锅仔动态表情包</title>\n<style>' + css + '</style>\n</head>\n<body>\n'
    '<div class="header"><h1>🍲 锅仔动态表情包（24 张 GIF）</h1>'
    '<p>240×240 · 12帧循环 · 呼吸跳动 + 星星闪烁 · 统一风格</p></div>\n'
    '<div class="section-title">动态表情</div>\n<div class="grid">' + rows + '</div>\n'
    '<div class="footer">锅仔动态表情包 · 点击任意 GIF 放大预览动画</div>\n'
    '<div class="modal" id="modal" onclick="closeModal()"><button class="modal-close" onclick="closeModal()">&times;</button><img id="modalImg" src=""></div>\n'
    '<script>' + js + '</script>\n</body>\n</html>\n'
)

with open(os.path.join(d, "动态表情预览.html"), "w", encoding="utf-8") as f:
    f.write(html)
print("preview done, gifs:", len(gifs))
