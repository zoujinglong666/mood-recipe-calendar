# -*- coding: utf-8 -*-
"""Test mobile site and referer variants."""
import requests, time, re
UA_PC = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
UA_M = "Mozilla/5.0 (iPhone; CPU iPhone OS 16_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.0 Mobile/15E148 Safari/604.1"

# Test 1: mobile site
print("=== Test mobile site ===")
s = requests.Session()
s.headers.update({"User-Agent": UA_M, "Accept-Language": "zh-CN,zh;q=0.9"})
try:
    r = s.get("https://m.xiachufang.com/", timeout=20)
    print("mobile home:", r.status_code, len(r.text))
    time.sleep(4)
    r = s.get("https://m.xiachufang.com/recipe/100124682/", timeout=20)
    print("mobile recipe:", r.status_code, len(r.text), "滑动:", "滑动验证" in r.text, "ings:", "ings" in r.text)
except Exception as e:
    print("mobile err:", e)

time.sleep(5)

# Test 2: PC site with search-referer chain
print("\n=== Test PC with search referer ===")
s2 = requests.Session()
s2.headers.update({"User-Agent": UA_PC, "Accept-Language": "zh-CN,zh;q=0.9"})
s2.get("https://www.xiachufang.com/", timeout=20)
time.sleep(4)
# do a search
r = s2.get("https://www.xiachufang.com/search/?keyword=%E7%BA%A2%E7%83%A7%E8%82%89", timeout=20)
print("search:", len(r.text))
time.sleep(4)
# fetch recipe with search referer
r = s2.get("https://www.xiachufang.com/recipe/100124682/",
           headers={"Referer": "https://www.xiachufang.com/search/?keyword=%E7%BA%A2%E7%83%A7%E8%82%89"}, timeout=20)
print("pc recipe w/search-ref:", len(r.text), "滑动:", "滑动验证" in r.text, "h1:", "<h1>" in r.text)
