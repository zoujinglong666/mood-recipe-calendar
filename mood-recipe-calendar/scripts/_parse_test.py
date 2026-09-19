# -*- coding: utf-8 -*-
from bs4 import BeautifulSoup
import re
html = open("scripts/_test_recipe.html", encoding="utf-8").read()
print("len:", len(html), "滑动:", "滑动验证" in html)
soup = BeautifulSoup(html, "lxml")
h1 = soup.find("h1")
print("name:", h1.get_text(strip=True) if h1 else None)
imgs = list(dict.fromkeys(re.findall(r'https://i[12]\.chuimg\.com/[^"\'\s\\]+\.(?:jpg|png)', html)))
print("imgs:", imgs[:3])
ings_div = soup.select_one("div.ings")
print("ings:", bool(ings_div))
if ings_div:
    rows = ings_div.select("tr")
    print("ing rows:", len(rows))
    for tr in rows[:3]:
        nm = tr.select_one("td.name")
        unit = tr.select_one("td.unit")
        print("  ", nm.get_text(" ", strip=True) if nm else None, "|", unit.get_text(" ", strip=True) if unit else None)
steps = [p.get_text(strip=True) for p in soup.select("div.steps li p")]
print("steps:", len(steps))
