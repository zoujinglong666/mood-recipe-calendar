# -*- coding: utf-8 -*-
import requests, time, re
UA = ("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
      "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
H = {
    "User-Agent": UA,
    "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8",
    "Accept-Language": "zh-CN,zh;q=0.9",
    "Sec-Ch-Ua": '"Chromium";v="124", "Google Chrome";v="124", "Not-A.Brand";v="99"',
    "Sec-Ch-Ua-Mobile": "?0",
    "Sec-Ch-Ua-Platform": '"Windows"',
    "Sec-Fetch-Dest": "document",
    "Sec-Fetch-Mode": "navigate",
    "Sec-Fetch-Site": "none",
    "Sec-Fetch-User": "?1",
    "Upgrade-Insecure-Requests": "1",
}
s = requests.Session()
s.headers.update(H)
s.get("https://www.xiachufang.com/", timeout=20)
print("home ok"); time.sleep(10)

# Test: fetch recipe with HOME referer (no search done)
for rid in ["100124682", "103885768", "106495092"]:
    r = s.get(f"https://www.xiachufang.com/recipe/{rid}/",
              headers={"Referer": "https://www.xiachufang.com/", "Sec-Fetch-Site": "same-origin"},
              timeout=20)
    ok = "滑动验证" not in r.text and "ings" in r.text
    print(f"recipe {rid}: len={len(r.text)} ok={ok}")
    time.sleep(10)
