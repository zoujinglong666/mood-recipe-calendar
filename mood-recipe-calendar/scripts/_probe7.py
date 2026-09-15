# -*- coding: utf-8 -*-
import re, time, requests
from bs4 import BeautifulSoup
UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
s = requests.Session()
s.headers.update({"User-Agent": UA, "Accept-Language": "zh-CN,zh;q=0.9",
                  "Accept": "text/html,*/*;q=0.8", "Upgrade-Insecure-Requests": "1"})
s.get("https://www.xiachufang.com/", timeout=20); time.sleep(3)
r = s.get("https://www.xiachufang.com/recipe/100124682/", headers={"Referer":"https://www.xiachufang.com/"}, timeout=20)
soup = BeautifulSoup(r.text, "lxml")
# find 用料 heading then siblings
for h in soup.find_all(["h2","h3","h4","div"], string=re.compile("用料|食材")):
    print("HEADING:", h.name, h.get("class"), "->", h.get_text(strip=True)[:30])
# dump the ingredient list region
ings_div = soup.select_one("div.ings")
print("div.ings found:", bool(ings_div))
if ings_div:
    print(ings_div.prettify()[:1500])
else:
    # try alternative
    cand = soup.select("ul li")
    for li in cand[:10]:
        t = li.get_text(" ", strip=True)
        if re.search(r'\d+\s*(个|g|ml|克|勺|适量|根|片|颗)', t):
            print("CAND LI:", t[:80])
