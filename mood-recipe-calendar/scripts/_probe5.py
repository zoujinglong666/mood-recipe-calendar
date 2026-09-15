# -*- coding: utf-8 -*-
import re, time, requests
from bs4 import BeautifulSoup

UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
s = requests.Session()
s.headers.update({"User-Agent": UA, "Accept-Language": "zh-CN,zh;q=0.9",
                   "Accept": "text/html,*/*;q=0.8", "Upgrade-Insecure-Requests": "1"})
s.get("https://www.xiachufang.com/", timeout=20)
time.sleep(2)

url = "https://www.xiachufang.com/recipe/104493058/"
r = s.get(url, headers={"Referer": "https://www.xiachufang.com/"}, timeout=20)
print("status", r.status_code, "len", len(r.text))
soup = BeautifulSoup(r.text, "lxml")

# name
h1 = soup.find("h1", class_="page-title") or soup.select_one("h1")
print("H1:", repr(h1.get_text(strip=True)) if h1 else None)

# cover image: look inside recipe main pic
pic = soup.select_one("div.recipe-show img") or soup.select_one("div#buy-actions + img")
print("PIC img tag:", pic)
# all chuimg imgs with classes
for img in soup.find_all("img", src=re.compile(r"chuimg"))[:8]:
    print("  IMG class=%s src=%s" % (img.get("class"), img.get("src")))

# ingredients block
ings = []
for li in soup.select("div.ings li"):
    name_a = li.select_one("a.name")
    unit = li.select_one("p.unit, .ingredient-unit")
    nm = name_a.get_text(strip=True) if name_a else ""
    un = unit.get_text(strip=True) if unit else ""
    # fallback: whole li text
    if not nm:
        nm = li.get_text(" ", strip=True)
    ings.append((nm, un))
print("INGS parsed:", ings)

# steps
steps = []
for li in soup.select("div.steps li"):
    p = li.select_one("p.text") or li.select_one("p")
    if p:
        steps.append(p.get_text(strip=True))
print("STEPS parsed n=", len(steps), steps[:2])

# meta: 耗时 / 难度 / 人数
for sel in [".cata", "div.recipe-info-container .cata", ".major2"]:
    for el in soup.select(sel):
        t = el.get_text(" ", strip=True)
        if any(k in t for k in ["时间","难度","人数","难度","焙","烤","炖","分"]):
            print("META[%s]:" % sel, t[:120])

# description
desc = soup.select_one("div.desc p, .recipe-show .desc")
print("DESC:", desc.get_text(" ", strip=True)[:120] if desc else None)
