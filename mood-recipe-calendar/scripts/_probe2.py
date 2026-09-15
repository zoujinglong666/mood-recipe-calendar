# -*- coding: utf-8 -*-
import re, requests, time

UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
HEADERS = {
    "User-Agent": UA,
    "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8",
    "Accept-Language": "zh-CN,zh;q=0.9,en;q=0.8",
    "Upgrade-Insecure-Requests": "1",
    "Sec-Fetch-Dest": "document",
    "Sec-Fetch-Mode": "navigate",
    "Sec-Fetch-Site": "none",
    "Sec-Fetch-User": "?1",
}
s = requests.Session()
s.headers.update(HEADERS)

print("== GET homepage ==")
r = s.get("https://www.xiachufang.com/", timeout=20)
print("home", r.status_code, len(r.text))
print("cookies:", s.cookies.get_dict())
print("body head:", r.text[:300].replace("\n"," "))

time.sleep(2)
print("== GET search ==")
r2 = s.get("https://www.xiachufang.com/search/", params={"keyword": "番茄炒蛋"},
           headers={"Referer": "https://www.xiachufang.com/"}, timeout=20)
print("search", r2.status_code, len(r2.text))
print("body head:", r2.text[:400].replace("\n"," "))
ids = re.findall(r'/recipe/(\d+)/', r2.text)
print("ids:", list(dict.fromkeys(ids))[:10])
