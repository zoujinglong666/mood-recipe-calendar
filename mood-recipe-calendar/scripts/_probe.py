# -*- coding: utf-8 -*-
import sys, re, requests
from bs4 import BeautifulSoup

HEADERS = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36",
    "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8",
    "Accept-Language": "zh-CN,zh;q=0.9",
    "Referer": "https://www.xiachufang.com/",
    "Upgrade-Insecure-Requests": "1",
}
s = requests.Session()
s.headers.update(HEADERS)

def show_recipe(url):
    r = s.get(url, timeout=20)
    print("STATUS", r.status_code, "LEN", len(r.text))
    soup = BeautifulSoup(r.text, "lxml")
    h = soup.find("h1", class_=re.compile("recipe")) or soup.find("h1")
    print("NAME:", h.get_text(strip=True) if h else None)
    # main image
    cover = soup.find("img", src=re.compile(r"chuimg\.com"))
    print("IMG:", cover["src"] if cover else None)
    # ingredients
    ings = []
    for li in soup.select("div.ings li"):
        parts = li.get_text(" ", strip=True).split()
        ings.append(" ".join(parts))
    print("INGS:", ings[:8], "count=", len(ings))
    # steps
    steps = [p.get_text(strip=True) for p in soup.select("div.steps p.text")]
    print("STEPS:", len(steps), steps[:2])
    # time / difficulty
    info = soup.select("div.recipe-show .cata")
    for it in info[:6]:
        print("  INFO:", it.get_text(" ", strip=True)[:80])

def show_search(kw):
    r = s.get("https://www.xiachufang.com/search/", params={"keyword": kw}, timeout=20)
    print("SEARCH", kw, "STATUS", r.status_code, "LEN", len(r.text))
    soup = BeautifulSoup(r.text, "lxml")
    links = set(re.findall(r'/recipe/(\d+)/', r.text))
    print("RECIPE IDS on page:", list(links)[:12], "total", len(links))
    imgs = re.findall(r'https://i[12]\.chuimg\.com/[^"\'\s]+\.(?:jpg|png)', r.text)
    print("IMGS sample:", imgs[:3])

if __name__ == "__main__":
    show_search("番茄炒蛋")
    print("="*40)
    show_recipe("https://www.xiachufang.com/recipe/101857270/")
