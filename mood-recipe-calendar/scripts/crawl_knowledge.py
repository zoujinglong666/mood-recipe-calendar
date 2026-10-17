# -*- coding: utf-8 -*-
"""
crawl_knowledge.py — 抓取公开权威机构的营养/膳食知识，生成 guideline_chunks 兼容的知识点 JSON。

来源与合规（设计原则）：
  - who  ：世界卫生组织中文事实档案（who.int，官方公开、鼓励传播引用）。
           内容版权 © WHO——与 build_guideline_rag.py 处理膳食指南 PDF 同一模式：
           改写成自家知识点 + 每条随库署名（sourceName/sourceUrl），不逐字复制。
  - wiki ：中文维基百科（CC BY-SA 4.0，可商用需署名；署名由落库的
           sourceName/sourceUrl 在前端「依据卡片」自然展示）。
           大陆网络环境通常不可达，作为可选源保留。

产出与 build_guideline_rag.py --from-json 完全兼容，直接接现有向量化+落库管线：
  ZHIPU_API_KEY=xx python3 scripts/build_guideline_rag.py \\
    --from-json scripts/who_knowledge.json \\
    --out scripts/who_knowledge_embedded.json --db
落库后调 POST /api/ops/knowledge/rebuild（X-Ops-Token）热生效。

用法：
  python3 scripts/crawl_knowledge.py                          # WHO 源 + 启发式抽取
  ZHIPU_API_KEY=xx python3 scripts/crawl_knowledge.py --llm   # WHO 源 + LLM 改写（质量更高）
  python3 scripts/crawl_knowledge.py --source wiki            # 维基源（需可访问的网络/代理）
  python3 scripts/crawl_knowledge.py --source who --limit 3   # 只抓前 N 篇
  python3 scripts/crawl_knowledge.py --source who --from-cache
"""
import argparse
import json
import os
import re
import ssl
import time
import urllib.parse
import urllib.request
from pathlib import Path

# macOS 自带 Python 常缺 CA 证书；与 build_guideline_rag.py 同款处理：
# 本地一次性构建脚本放宽证书校验，不影响生产（生产走 Java 侧 ZhipuEmbeddingClient）。
ssl._create_default_https_context = ssl._create_unverified_context

try:
    from bs4 import BeautifulSoup
except ImportError:
    BeautifulSoup = None

SCRIPTS = Path(__file__).resolve().parent
UA = "GuoZaiKnowledgeBot/1.0 (https://moodrecipe.icu; knowledge build for recipe app)"

CATEGORIES = {"谷薯类", "蔬菜水果", "鱼禽肉蛋", "奶豆坚果", "油盐糖",
              "饮水", "运动体重", "三餐规律", "特殊人群", "其他"}

# WHO 中文事实档案：(slug, 显示名, 默认类别提示)
WHO_FACTSHEETS = [
    ("healthy-diet", "健康饮食", "三餐规律"),
    ("salt-reduction", "减盐", "油盐糖"),
    ("obesity-and-overweight", "肥胖和超重", "特殊人群"),
    ("physical-activity", "身体活动", "运动体重"),
    ("food-safety", "食品安全", "其他"),
    ("diabetes", "糖尿病", "特殊人群"),
    ("cardiovascular-diseases-(cvds)", "心血管疾病", "特殊人群"),
    ("anaemia", "贫血", "鱼禽肉蛋"),
    ("cancer", "癌症预防", "特殊人群"),
]

# 中文维基百科条目（需可访问的网络）：(条目名, 默认类别提示)
WIKI_ARTICLES = [
    ("膳食营养素参考摄入量", "特殊人群"),
    ("健康饮食", "三餐规律"),
    ("营养素", "其他"),
    ("膳食纤维", "谷薯类"),
    ("蛋白质（营养学）", "鱼禽肉蛋"),
    ("脂肪", "油盐糖"),
    ("碳水化合物", "谷薯类"),
    ("维生素", "蔬菜水果"),
    ("维生素C", "蔬菜水果"),
    ("维生素D", "其他"),
    ("钙", "奶豆坚果"),
    ("铁", "鱼禽肉蛋"),
    ("升糖指数", "谷薯类"),
    ("反式脂肪", "油盐糖"),
    ("饱和脂肪", "油盐糖"),
    ("地中海饮食", "三餐规律"),
    ("得舒饮食", "特殊人群"),
    ("超加工食品", "油盐糖"),
]

SKIP_SECTIONS = {"参考文献", "外部链接", "参见", "注释", "延伸阅读", "参考资料", "相关条目",
                 "world health assembly", "references", "related links"}


# ---------- HTTP ----------
def http_text(url, retries=2):
    req = urllib.request.Request(url, headers={"User-Agent": UA, "Accept-Language": "zh-CN,zh;q=0.9"})
    last = None
    for attempt in range(retries + 1):
        try:
            with urllib.request.urlopen(req, timeout=30) as resp:
                return resp.read().decode("utf-8", errors="replace")
        except Exception as e:
            last = e
            time.sleep(1.5 * (attempt + 1))
    print(f"  [http err] {url[:100]}... {last}", flush=True)
    return None


# ---------- WHO 源 ----------
def fetch_who(slug, name, hint):
    html = http_text(f"https://www.who.int/zh/news-room/fact-sheets/detail/{slug}")
    if not html:
        return None
    # 截取正文区域，剥离脚本/样式
    html = re.sub(r"<(script|style|nav|footer|header)[\s\S]*?</\1>", "", html, flags=re.I)
    if BeautifulSoup is not None:
        soup = BeautifulSoup(html, "html.parser")
        main = soup.find("main") or soup.find("article") or soup
        text_parts = []
        for tag in main.find_all(["h2", "h3", "p", "li"]):
            t = tag.get_text(" ", strip=True)
            if t:
                text_parts.append(f"== {t} ==" if tag.name in ("h2", "h3") else t)
        text = "\n".join(text_parts)
    else:
        text = re.sub(r"<[^>]+>", "\n", html)
        text = re.sub(r"\n{2,}", "\n", text)
    if len(text) < 500:
        return None
    return {
        "requested": slug,
        "resolved": name,
        "url": f"https://www.who.int/zh/news-room/fact-sheets/detail/{slug}",
        "hint": hint,
        "text": text,
    }


def fetch_all_who(limit=0):
    targets = WHO_FACTSHEETS[:limit] if limit else WHO_FACTSHEETS
    raw = []
    for slug, name, hint in targets:
        art = fetch_who(slug, name, hint)
        if art:
            print(f"  [ok] WHO《{name}》（{len(art['text'])} 字）", flush=True)
            raw.append(art)
        else:
            print(f"  [skip] WHO {slug}", flush=True)
        time.sleep(1.0)
    return raw


# ---------- 维基源（可选） ----------
def fetch_all_wiki(limit=0):
    targets = WIKI_ARTICLES[:limit] if limit else WIKI_ARTICLES
    raw = []
    for title, hint in targets:
        data = http_text("https://zh.wikipedia.org/w/api.php?" + urllib.parse.urlencode({
            "action": "query", "format": "json", "prop": "extracts",
            "explaintext": 1, "redirects": 1, "titles": title}))
        art = None
        if data:
            try:
                pages = (json.loads(data).get("query") or {}).get("pages") or {}
                for _, page in pages.items():
                    if page.get("extract"):
                        art = {
                            "requested": title,
                            "resolved": page.get("title", title),
                            "url": "https://zh.wikipedia.org/wiki/" + urllib.parse.quote(page.get("title", title).replace(" ", "_")),
                            "hint": hint,
                            "text": page["extract"],
                        }
            except Exception:
                pass
        if art:
            print(f"  [ok] 维基《{art['resolved']}》（{len(art['text'])} 字）", flush=True)
            raw.append(art)
        else:
            print(f"  [skip] 维基 {title}", flush=True)
        time.sleep(0.8)
    return raw


# ---------- 抽取 ----------
def split_sections(text):
    sections, current, buf = [], "概述", []
    for line in text.splitlines():
        m = re.match(r"^=+\s*(.+?)\s*=+$", line.strip())
        if m:
            if buf:
                sections.append((current, "\n".join(buf)))
            current, buf = m.group(1), []
        else:
            buf.append(line)
    if buf:
        sections.append((current, "\n".join(buf)))
    return [(s, b) for s, b in sections if s not in SKIP_SECTIONS]


def guess_category(text, hint):
    t = text.lower()
    rules = [
        (("盐", "钠", "糖", "油", "脂肪", "反式"), "油盐糖"),
        (("奶", "钙", "酸奶"), "奶豆坚果"),
        (("蔬菜", "水果", "维c", "维生素c", "叶酸"), "蔬菜水果"),
        (("肉", "蛋", "鱼", "铁", "锌", "蛋白", "贫血"), "鱼禽肉蛋"),
        (("主食", "谷物", "米", "面", "纤维", "升糖", "碳水"), "谷薯类"),
        (("早餐", "晚餐", "午餐", "三餐", "进食"), "三餐规律"),
        (("儿童", "老人", "孕妇", "痛风", "糖尿病", "心血管", "癌症", "肥胖"), "特殊人群"),
        (("运动", "活动"), "运动体重"),
        (("水", "饮水"), "饮水"),
    ]
    for keys, cat in rules:
        if any(k in t for k in keys):
            return cat
    return hint if hint in CATEGORIES else "其他"


def make_chunk(art, section, content, rewritten=False):
    return {
        "page": 0,  # 占位，落库前统一编号
        "section": f"{art['resolved']} · {section[:30]}",
        "category": "其他",
        "title": section if len(section) <= 30 else section[:28] + "…",
        "content": content,
        "tags": [art["resolved"]],
        "sourceName": f"世界卫生组织《{art['resolved']}》事实档案（who.int，改写整理）" if "who.int" in art["url"]
                      else f"维基百科《{art['resolved']}》（CC BY-SA 4.0{'，AI 改写' if rewritten else ''}）",
        "sourceUrl": art["url"],
        "embedding": None,
    }


def heuristic_points(articles):
    chunks = []
    for page, art in enumerate(articles, start=1):
        for section, body in split_sections(art["text"]):
            for para in re.split(r"\n\s*\n|\n(?=== )", body):
                para = re.sub(r"\s+", "", para)
                if not (50 <= len(para) <= 320):
                    continue
                if re.search(r"维基|编辑|条目|世卫组织版权|©|版权所有|print|share|电子邮箱|相关链接", para, re.I):
                    continue
                if not re.search(r"营养|饮食|膳食|摄入|健康|食物|食品|克|毫克|维生素|蛋白|脂肪|热量|卡路里|盐|糖|活动", para):
                    continue
                chunk = make_chunk(art, section if section != "概述" else art["resolved"], para)
                chunk["page"] = page
                chunk["category"] = guess_category(section + para, art.get("hint", "其他"))
                chunks.append(chunk)
    return chunks


# ---------- LLM 改写（可选，更高质量） ----------
def llm_distill(articles):
    key = os.environ.get("ZHIPU_API_KEY", "")
    if not key:
        return None
    chunks = []
    for page, art in enumerate(articles, start=1):
        prompt = (
            "你是营养知识编辑。把下面权威机构公开资料改写成面向「家常做饭的人」的知识点：\n"
            "要求：1) 每条 40~160 字，说人话、可执行；2) 改写而非逐字复制；3) 只保留有科学依据的"
            "饮食与生活建议，剔除疾病治疗、统计与机构介绍；4) category 只能是："
            + "、".join(sorted(CATEGORIES)) + "；5) 输出 JSON 数组，元素格式 "
            '{"category":"...","title":"20字内标题","content":"...","tags":["关键词"]}，最多 8 条，只输出 JSON。\n\n'
            "资料标题：" + art["resolved"] + "\n内容：\n" + art["text"][:7000]
        )
        body = json.dumps({"model": "glm-4-flash", "messages": [{"role": "user", "content": prompt}],
                           "temperature": 0.3}).encode("utf-8")
        req = urllib.request.Request(
            "https://open.bigmodel.cn/api/paas/v4/chat/completions", data=body,
            headers={"Content-Type": "application/json", "Authorization": f"Bearer {key}"})
        try:
            with urllib.request.urlopen(req, timeout=60) as resp:
                data = json.loads(resp.read().decode("utf-8"))
            text = data["choices"][0]["message"]["content"]
            text = re.sub(r"^```(json)?|```$", "", text.strip(), flags=re.M).strip()
            points = json.loads(text)
            if not isinstance(points, list):
                continue
            count = 0
            for p in points[:8]:
                content = (p.get("content") or "").strip()
                if not content:
                    continue
                cat = (p.get("category") or "其他").strip()
                chunk = make_chunk(art, (p.get("title") or art["resolved"])[:30], content, rewritten=True)
                chunk["page"] = page
                chunk["category"] = cat if cat in CATEGORIES else "其他"
                chunk["tags"] = [str(t) for t in (p.get("tags") or [art["resolved"]])][:6]
                chunks.append(chunk)
                count += 1
            print(f"  [llm ok] {art['resolved']} -> {count} 条", flush=True)
        except Exception as e:
            print(f"  [llm err] {art['resolved']}: {e}", flush=True)
        time.sleep(1.0)
    return chunks or None


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--source", default="who", choices=["who", "wiki"], help="知识来源（默认 WHO 中文事实档案）")
    ap.add_argument("--llm", action="store_true", help="用智谱 LLM 改写（默认启发式段落抽取）")
    ap.add_argument("--from-cache", action="store_true", help="复用上次抓取缓存，不重新请求")
    ap.add_argument("--limit", type=int, default=0, help="只处理前 N 篇（0=全部）")
    ap.add_argument("--out", default=None, help="输出 JSON 路径（默认按来源命名）")
    args = ap.parse_args()

    cache = SCRIPTS / f"knowledge_raw_{args.source}.json"
    out = Path(args.out) if args.out else SCRIPTS / f"{'who' if args.source == 'who' else 'wiki'}_knowledge.json"

    if args.from_cache and cache.exists():
        articles = json.loads(cache.read_text(encoding="utf-8"))
        print(f"从缓存加载 {len(articles)} 篇", flush=True)
    else:
        print(f"抓取来源 {args.source}，共 {args.limit or '全部'} 篇...", flush=True)
        articles = fetch_all_who(args.limit) if args.source == "who" else fetch_all_wiki(args.limit)
        cache.write_text(json.dumps(articles, ensure_ascii=False), encoding="utf-8")
        print(f"已缓存原始抓取结果：{cache}", flush=True)
    if not articles:
        print("没有抓到任何内容，退出", flush=True)
        return

    chunks = llm_distill(articles) if args.llm else None
    mode = "LLM 改写"
    if chunks is None:
        mode = "启发式抽取"
        chunks = heuristic_points(articles)
    print(f"{mode}完成：{len(chunks)} 条知识点", flush=True)

    out.write_text(json.dumps(chunks, ensure_ascii=False, indent=1), encoding="utf-8")
    print(f"已写出：{out}", flush=True)
    print("下一步（向量化+落库，勿加 --reset 以免清空膳食指南数据）：", flush=True)
    print(f"  ZHIPU_API_KEY=xx python3 scripts/build_guideline_rag.py "
          f"--from-json {out.name} --out {out.stem}_embedded.json --db", flush=True)


if __name__ == "__main__":
    main()
