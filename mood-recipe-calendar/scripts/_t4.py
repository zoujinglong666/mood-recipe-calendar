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

# Dump .step elements
print("=== .step elements ===")
for el in soup.select(".step")[:3]:
    print(repr(el.get_text(" ", strip=True)[:120]))

print("\n=== .steps p elements ===")
for el in soup.select(".steps p")[:5]:
    print(repr(el.get_text(" ", strip=True)[:120]))

# Find ingredients - look for text patterns
print("\n=== search ingredient containers ===")
for cls in ["ings", "ingredient", "material", "pei", "liao"]:
    for el in soup.select(f"[class*={cls}]"):
        txt = el.get_text(" ", strip=True)
        if len(txt) < 300 and re.search(r'\d+\s*(克|g|ml|个|勺)', txt):
            print(f"[class*={cls}] ->", repr(txt[:150]))
            break

# Look for li with ingredient-like content
print("\n=== li with ingredient text ===")
count = 0
for li in soup.find_all("li"):
    t = li.get_text(" ", strip=True)
    if re.search(r'(克|g|ml|个|勺|适量)', t) and len(t) < 60 and count < 8:
        print("  ", repr(t))
        count += 1

# Better image: look for og:image or recipe cover
print("\n=== og:image ===")
og = soup.find("meta", property="og:image")
print(og.get("content") if og else None)
# recipe cover image - look for img in recipe-content
for img in soup.select("img"):
    src = img.get("src") or img.get("data-src") or ""
    if "chuimg" in src and ("_2720w" in src or "_1080w" in src):
        print("cover img:", src)
        break
