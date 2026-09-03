# -*- coding: utf-8 -*-
import os
d = r"C:/Users/DELL/DoubaoWork/chats/2026-09-01/new-chat/IP视觉资产/表情包/微信上架"
main_dir = os.path.join(d, "主图240")
mains = sorted(os.listdir(main_dir))

css = """
*{margin:0;padding:0;box-sizing:border-box}
body{background:#FDF6F0;font-family:"PingFang SC","Microsoft YaHei",sans-serif;color:#5A3E2B;padding:40px 20px}
.header{text-align:center;margin-bottom:40px}
.header h1{font-size:32px;color:#5A3E2B;margin-bottom:8px}
.header p{color:#8B6B55;font-size:15px}
.section-title{font-size:20px;color:#E8836B;margin:36px 0 16px;padding-left:12px;border-left:4px solid #FFB88C}
.banner{max-width:900px;margin:0 auto 20px;border-radius:16px;overflow:hidden;box-shadow:0 4px 20px rgba(232,131,107,0.15);cursor:pointer}
.banner img{width:100%;display:block}
.grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(150px,1fr));gap:20px}
.card{background:#FDE6D4;border-radius:12px;overflow:hidden;box-shadow:0 2px 12px rgba(232,131,107,0.1);transition:transform .2s;cursor:pointer}
.card:hover{transform:translateY(-3px)}
.card img{width:100%;display:block;aspect-ratio:1/1;object-fit:cover}
.card-label{padding:8px;font-size:12px;color:#5A3E2B;text-align:center;font-weight:500}
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

rows = ""
for s in mains:
    rows += '<div class="card" onclick="openModal(\'主图240/{0}\')"><img src="主图240/{0}"><div class="card-label">{1}</div></div>\n'.format(s, s.replace(".png", ""))

html = (
    '<!DOCTYPE html>\n<html lang="zh-CN">\n<head>\n<meta charset="UTF-8">\n'
    '<meta name="viewport" content="width=device-width, initial-scale=1.0">\n'
    '<title>锅仔表情包 · 微信上架包</title>\n<style>' + css + '</style>\n</head>\n<body>\n'
    '<div class="header"><h1>🍲 锅仔表情包 · 微信上架包</h1>'
    '<p>24 张主图 240×240 · 缩略图 120×120 · 封面 · 聊天图标 · 详情页横幅 750×400</p></div>\n'
    '<div class="section-title">详情页横幅（750×400）</div>\n'
    '<div class="banner" onclick="openModal(\'详情页横幅_750x400.png\')"><img src="详情页横幅_750x400.png"></div>\n'
    '<div class="section-title">封面图（240×240）与聊天面板图标（50×50）</div>\n'
    '<div class="grid">\n'
    '<div class="card" onclick="openModal(\'封面图_240.png\')"><img src="封面图_240.png"><div class="card-label">封面图 240×240</div></div>\n'
    '<div class="card" onclick="openModal(\'聊天面板图标_50.png\')"><img src="聊天面板图标_50.png"><div class="card-label">聊天图标 50×50</div></div>\n'
    '</div>\n'
    '<div class="section-title">24 张表情主图（240×240）</div>\n'
    '<div class="grid">' + rows + '</div>\n'
    '<div class="footer">锅仔表情包微信上架包 · 点击任意图片放大</div>\n'
    '<div class="modal" id="modal" onclick="closeModal()"><button class="modal-close" onclick="closeModal()">&times;</button><img id="modalImg" src=""></div>\n'
    '<script>' + js + '</script>\n</body>\n</html>\n'
)

with open(os.path.join(d, "微信上架预览.html"), "w", encoding="utf-8") as f:
    f.write(html)
print("preview done, mains:", len(mains))
