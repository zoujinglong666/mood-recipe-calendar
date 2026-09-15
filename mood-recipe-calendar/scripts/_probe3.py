# -*- coding: utf-8 -*-
import re, requests, time
from bs4 import BeautifulSoup

UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
HEADERS = {
    "User-Agent": UA,
    "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8",
    "Accept-Language": "zh-CN,zh;q=0.9,en;q=0.8",
    "Upgrade-Insecure-Requests": "1",
    "Sec-Fetch-Dest": "document", "Sec-Fetch-Mode": "navigate",
    "Sec-Fetch-Site": "none", "Sec-Fetch-User": "?1",
}
s = requests.Session()
s.headers.update(HEADERS)
s.get("https://www.xiachufang.com/", timeout=20)
time.sleep(1.5)

url = "https://www.xiachufang.com/recipe/104493058/"
r = s.get(url, headers={"Referer": "https://www.xiachufang.com/search/?keyword=%E7%95%AA%E8%8C%84%E7%82%92%E8%9B%8B"}, timeout=20)
print("STATUS", r.status_code, "LEN", len(r.text))
soup = BeautifulSoup(r.text, "lxml")

h = soup.find("h1")
print("NAME:", h.get_text(strip=True) if h else None)

# cover: the recipe main image
cover = soup.select_one("div.recipe-show img, .recipe-pic img, img.recipe-img")
if cover:
    print("COVER:", cover.get("src") or cover.get("data-src"))

# all chuimg images
imgs = re.findall(r'https://i[12]\.chuimg\.com/[^"\'\s\\]+\.(?:jpg|png)', r.text)
print("ALL IMGS:", imgs[:6])

# ingredients
ings = []
for li in soup.select("div.ings li"):
    a = li.select_one("a.name")
    q = li.select_one("p") or li.select_one(".unit")
    name = a.get_text(strip=True) if a else li.get_text(" ", strip=True)
    unit = q.get_text(strip=True) if q else ""
    ings.append(f"{name} {unit}".strip())
print("INGS:", ings)

# steps
steps = [p.get_text(strip=True) for p in soup.select("div.steps li p, div.steps p.text")]
print("STEPS n=", len(steps))
for st in steps[:3]:
    print("  -", st)

# meta: time, difficulty
for block in soup.select(".recipe-show .cata, .recipe-detail .cata"):
    print("CATA:", block.get_text(" ", strip=True)[:100])
# try general info row
info = soup.select(".recipe-info-container .cata")
for i in info:
    print("INFO:", i.get_text(" ", strip=True)[:100])
