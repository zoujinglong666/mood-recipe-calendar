# -*- coding: utf-8 -*-
import requests, time
UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
s = requests.Session()
s.headers.update({"User-Agent": UA, "Accept-Language": "zh-CN,zh;q=0.9"})
s.get("https://www.xiachufang.com/", timeout=20)
time.sleep(5)
r = s.get("https://www.xiachufang.com/recipe/100124682/",
          headers={"Referer": "https://www.xiachufang.com/"}, timeout=20)
print("len", len(r.text), "滑动验证", "滑动验证" in r.text)
print("has h1", "<h1>" in r.text, "has ings", "ings" in r.text)
