# -*- coding: utf-8 -*-
import requests, time
UA = ("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
      "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
s = requests.Session()
s.headers.update({
    "User-Agent": UA,
    "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8",
    "Accept-Language": "zh-CN,zh;q=0.9",
    "Accept-Encoding": "gzip, deflate, br",
    "Cache-Control": "no-cache",
    "Sec-Ch-Ua": '"Chromium";v="124", "Google Chrome";v="124", "Not-A.Brand";v="99"',
    "Sec-Ch-Ua-Mobile": "?0",
    "Sec-Ch-Ua-Platform": '"Windows"',
    "Sec-Fetch-Dest": "document",
    "Sec-Fetch-Mode": "navigate",
    "Sec-Fetch-Site": "none",
    "Sec-Fetch-User": "?1",
    "Upgrade-Insecure-Requests": "1",
    "Pragma": "no-cache",
})
r = s.get("https://www.xiachufang.com/", timeout=20)
print("home", r.status_code, len(r.text))
time.sleep(6)
r = s.get("https://www.xiachufang.com/recipe/100124682/",
          headers={"Referer": "https://www.xiachufang.com/",
                   "Sec-Fetch-Site": "same-origin"}, timeout=20)
print("recipe", len(r.text), "滑动:", "滑动验证" in r.text, "ings:", "ings" in r.text)
