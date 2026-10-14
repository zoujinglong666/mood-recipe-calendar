#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""尝试爬取美食杰菜谱列表"""
import urllib.request, re, json, time

headers = {
    'User-Agent': 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
    'Accept': 'text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8',
    'Accept-Language': 'zh-CN,zh;q=0.9,en;q=0.8',
}

# 试试美食杰的菜谱列表页
url = "https://www.meishij.net/list/"
req = urllib.request.Request(url, headers=headers)
try:
    resp = urllib.request.urlopen(req, timeout=10)
    html = resp.read().decode('utf-8', errors='ignore')
    print(f"Status: {resp.status}, Length: {len(html)}")
    # 找菜谱链接
    links = re.findall(r'href="(/shicai/\d+[^"]*)"', html)
    print(f"Found links: {len(links)}")
    for l in links[:5]:
        print(f"  {l}")
except Exception as e:
    print(f"Error: {e}")

# 试试下厨房
url2 = "https://www.xiachufang.com/explore/"
req2 = urllib.request.Request(url2, headers=headers)
try:
    resp2 = urllib.request.urlopen(req2, timeout=10)
    html2 = resp2.read().decode('utf-8', errors='ignore')
    print(f"\nXiachufang Status: {resp2.status}, Length: {len(html2)}")
    recipes = re.findall(r'href="(/recipe/\d+[^"]*)"', html2)
    print(f"Found recipes: {len(recipes)}")
    for r in recipes[:5]:
        print(f"  {r}")
except Exception as e:
    print(f"Xiachufang Error: {e}")
