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
r = s.get("https://www.xiachufang.com/", timeout=20)
print("home", len(r.text))
time.sleep(6)
# search
r = s.get("https://www.xiachufang.com/search/?keyword=%E5%AE%B6%E5%B8%B8%E8%8F%9C",
          headers={"Referer": "https://www.xiachufang.com/", "Sec-Fetch-Site": "same-origin"}, timeout=20)
print("search", len(r.text))
ids = list(dict.fromkeys(re.findall(r"/recipe/(\d+)/", r.text)))[:5]
print("ids:", ids)
time.sleep(7)
# try first recipe from search
r = s.get(f"https://www.xiachufang.com/recipe/{ids[0]}/",
          headers={"Referer": "https://www.xiachufang.com/search/?keyword=%E5%AE%B6%E5%B8%B8%E8%8F%9C",
                   "Sec-Fetch-Site": "same-origin"}, timeout=20)
print(f"recipe {ids[0]}", len(r.text), "滑动:", "滑动验证" in r.text)
time.sleep(7)
# try known good recipe
r = s.get("https://www.xiachufang.com/recipe/100124682/",
          headers={"Referer": "https://www.xiachufang.com/", "Sec-Fetch-Site": "same-origin"}, timeout=20)
print("recipe 100124682", len(r.text), "滑动:", "滑动验证" in r.text)
