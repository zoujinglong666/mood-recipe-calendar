# -*- coding: utf-8 -*-
import re, time, requests
from bs4 import BeautifulSoup

UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
def newsession():
    s = requests.Session()
    s.headers.update({"User-Agent": UA, "Accept-Language": "zh-CN,zh;q=0.9",
                      "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
                      "Upgrade-Insecure-Requests": "1"})
    s.get("https://www.xiachufang.com/", timeout=20)
    return s

def fetch(s, url):
    r = s.get(url, headers={"Referer": "https://www.xiachufang.com/"}, timeout=20)
    return r

s = newsession()
print("home ok, bid=", s.cookies.get_dict())
time.sleep(4)

# search once
r = s.get("https://www.xiachufang.com/search/", params={"keyword": "红烧肉"}, timeout=20)
ids = list(dict.fromkeys(re.findall(r'/recipe/(\d+)/', r.text)))[:3]
print("search ids:", ids)
time.sleep(4)

# fetch first recipe
r = fetch(s, f"https://www.xiachufang.com/recipe/{ids[0]}/")
print("recipe", ids[0], "status", r.status_code, "len", len(r.text),
      "captcha=", ("滑动验证" in r.text))
if "滑动验证" not in r.text and len(r.text) > 20000:
    soup = BeautifulSoup(r.text, "lxml")
    h1 = soup.find("h1")
    print("NAME:", h1.get_text(strip=True) if h1 else None)
    imgs = re.findall(r'https://i[12]\.chuimg\.com/[^"\'\s\\]+\.(?:jpg|png)', r.text)
    print("IMG n=", len(imgs), imgs[:3])
    ings = []
    for li in soup.select("div.ings li"):
        nm = (li.select_one("a.name") or li).get_text(" ", strip=True)
        ings.append(nm)
    print("INGS:", ings[:6])
    steps = [p.get_text(strip=True) for p in soup.select("div.steps li p")]
    print("STEPS n=", len(steps))
