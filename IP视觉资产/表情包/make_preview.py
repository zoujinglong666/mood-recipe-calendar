# -*- coding: utf-8 -*-
import os
d = r"C:/Users/DELL/DoubaoWork/chats/2026-09-01/new-chat/IP视觉资产/表情包"
singles = sorted(os.listdir(os.path.join(d, "单张表情")))
grids = [
    "A组_害羞思考惊讶生气大哭飞吻得意哈欠酷.png",
    "B组_星星眼拜托干饭胜利抱抱累倒叉腰点头安利.png",
    "C组_问号装睡OK转圈托腮爱心举手跳舞发抖.png",
]

cards = ""
for g in grids:
    label = g.split("_")[0] + " 组拼图 · 9 表情"
    cards += '<div class="card" onclick="openModal(\'{0}\')"><img src="{0}"><div class="card-label">{1}</div></div>\n'.format(g, label)

rows = ""
for s in singles:
    label = s.replace(".png", "")
    rows += '<div class="card" onclick="openModal(\'单张表情/{0}\')"><img src="单张表情/{0}"><div class="card-label">{1}</div></div>\n'.format(s, label)

css = """
*{margin:0;padding:0;box-sizing:border-box}
body{background:#FDF6F0;font-family:"PingFang SC","Microsoft YaHei",sans-serif;color:#5A3E2B;padding:40px 20px}
.header{text-align:center;margin-bottom:40px}
.header h1{font-size:32px;color:#5A3E2B;margin-bottom:8px}
.header p{color:#8B6B55;font-size:15px}
.section-title{font-size:20px;color:#E8836B;margin:36px 0 16px;padding-left:12px;border-left:4px solid #FFB88C}
.grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(200px,1fr));gap:24px}
.card{background:#FDE6D4;border-radius:16px;overflow:hidden;box-shadow:0 2px 12px rgba(232,131,107,0.1);transition:transform .2s;cursor:pointer}
.card:hover{transform:translateY(-4px)}
.card img{width:100%;display:block;aspect-ratio:1/1;object-fit:cover}
.card-label{padding:10px 12px;font-size:13px;color:#5A3E2B;text-align:center;font-weight:500}
.modal{display:none;position:fixed;top:0;left:0;width:100%;height:100%;background:rgba(90,62,43,0.85);z-index:1000;justify-content:center;align-items:center;padding:20px}
.modal.active{display:flex}
.modal img{max-height:90vh;max-width:90vw;border-radius:12px;background:#fff}
.modal-close{position:absolute;top:20px;right:28px;color:#fff;font-size:32px;cursor:pointer;background:none;border:none}
.footer{text-align:center;margin-top:48px;color:#8B6B55;font-size:13px}
"""

js = """
function openModal(s){document.getElementById("modalImg").src=s;document.getElementById("modal").classList.add("active")}
function closeModal(){document.getElementById("modal").classList.remove("active")}
document.addEventListener("keydown",function(e){if(e.key==="Escape")closeModal()})
"""

head = ('<!DOCTYPE html>\n<html lang="zh-CN">\n<head>\n<meta charset="UTF-8">\n'
        '<meta name="viewport" content="width=device-width, initial-scale=1.0">\n'
        '<title>锅仔表情包</title>\n<style>' + css + '</style>\n</head>\n<body>\n')

body = ('<div class="header"><h1>🍲 锅仔表情包（27 张）</h1>'
        '<p>三组 · 纯白底 · 统一 Core-IP 风格 · 已裁切为 800×800 单张 PNG</p></div>\n'
        '<div class="section-title">拼图总览</div>\n<div class="grid">' + cards + '</div>\n'
        '<div class="section-title">单张表情（可直接用于微信表情包素材）</div>\n<div class="grid">' + rows + '</div>\n'
        '<div class="footer">锅仔表情包 · 点击任意图片放大</div>\n')

tail = ('<div class="modal" id="modal" onclick="closeModal()">'
        '<button class="modal-close" onclick="closeModal()">&times;</button>'
        '<img id="modalImg" src=""></div>\n'
        '<script>' + js + '</script>\n</body>\n</html>\n')

html = head + body + tail

with open(os.path.join(d, "表情包预览.html"), "w", encoding="utf-8") as f:
    f.write(html)
print("HTML generated, singles:", len(singles))
