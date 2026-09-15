# -*- coding: utf-8 -*-
import requests, time, re
from bs4 import BeautifulSoup
UA_M = "Mozilla/5.0 (iPhone; CPU iPhone OS 16_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.0 Mobile/15E148 Safari/604.1"
s = requests.Session()
s.headers.update({"User-Agent": UA_M, "Accept-Language": "zh-CN,zh;q=0.9"})
s.get("https://m.xiachufang.com/", timeout=20)
time.sleep(4)
r = s.get("https://m.xiachufang.com/recipe/100124682/", timeout=20)
soup = BeautifulSoup(r.text, "lxml")

# name
h1 = soup.find("h1")
print("h1:", h1.get_text(strip=True) if h1 else None)

# image
imgs = list(dict.fromkeys(re.findall(r'https://i[12]\.chuimg\.com/[^"\'\s\\]+\.(?:jpg|png)', r.text)))
print("imgs n=", len(imgs), imgs[:3])

# ingredients - check structure
ings_div = soup.select_one("div.ings")
print("div.ings:", bool(ings_div))
if ings_div:
    rows = ings_div.select("tr")
    print("tr rows:", len(rows))
    for tr in rows[:3]:
        nm = tr.select_one("td.name")
        unit = tr.select_one("td.unit")
        print("  ", nm.get_text(" ", strip=True) if nm else None, "|", unit.get_text(" ", strip=True) if unit else None)
else:
    # try other structures
    for sel in ["div.ings li", "ul.ings li", ".ingredient", "li.ingredient"]:
        found = soup.select(sel)
        print(f"selector {sel}: {len(found)}")

# steps
steps = [p.get_text(strip=True) for p in soup.select("div.steps li p")]
print("steps n=", len(steps))
if not steps:
    for sel in ["div.steps li", ".step", ".steps p", "ol li p"]:
        found = soup.select(sel)
        print(f"step selector {sel}: {len(found)}")
