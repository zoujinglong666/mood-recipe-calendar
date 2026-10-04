#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
《中国居民膳食指南》RAG 知识库构建脚本（离线，一次性）。

流程：
  1. 用 PyMuPDF 把扫描版 PDF 每页渲染为 JPEG（默认宽 1200px），转 base64。
  2. 每页调用 agnes 网关的多模态模型，抽取「经过改写的知识点」JSON（不复制原文）。
  3. 把所有知识点文本批量送智谱官方 embedding 向量化。
  4. 输出 JSON 文件，并可选地直接写入 MySQL（表结构与 Java 实体一致）。

合规边界：只入库改写后的要点，不存指南原文逐字内容；每条带 source_name/source_url 可追溯。

用法：
  # 小样本验证（无需智谱 key，仅验证视觉抽取链路，embedding 会跳过）
  HTTPS_PROXY=http://127.0.0.1:4780 python3 scripts/build_guideline_rag.py \
      --pdf "/path/2026年中国居民膳食指南.pdf" --limit 3 --no-db

  # 全量 378 页（需要 ZHIPU_API_KEY）
  HTTPS_PROXY=http://127.0.0.1:4780 ZHIPU_API_KEY=sk-xxx python3 scripts/build_guideline_rag.py \
      --pdf "/path/2026年中国居民膳食指南.pdf" --db
"""
import argparse
import ast
import base64
import json
import os
import ssl
import sys
import time
import urllib.request
import urllib.error
from concurrent.futures import ThreadPoolExecutor, as_completed

try:
    import fitz  # PyMuPDF
except ImportError:
    sys.exit("需要 PyMuPDF：python3 -m pip install --user pymupdf")

try:
    import pymysql
except ImportError:
    pymysql = None

PAGE_WIDTH = 1200
JPEG_QUALITY = 80
VISION_MODEL = os.environ.get("AI_VISION_MODEL") or "agnes-2.5-flash"
AGNES_BASE = os.environ.get("AI_RECIPE_BASE_URL", "https://apihub.agnes-ai.com/v1/chat/completions")
ZHIPU_BASE = os.environ.get("ZHIPU_EMBEDDING_BASE_URL", "https://open.bigmodel.cn/api/paas/v4")
ZHIPU_MODEL = os.environ.get("ZHIPU_EMBEDDING_MODEL", "embedding-2")
SOURCE_NAME = "中国居民膳食指南"
SOURCE_URL = "https://www.cnsoc.org/"

EXTRACT_PROMPT = """你正在解析《中国居民膳食指南》的一页扫描图。请从中抽取可操作、事实性的膳食营养建议要点。

严格要求：
1. 只抽取本页明确出现的营养/膳食建议、食物摄入量、搭配原则、人群注意事项等要点。
2. 每条要点必须用你自己的话改写（1-3 句，不要逐字复制原文，禁止大段引用）。
3. 忽略封面、目录、页码、广告、纯装饰图、空白页；若本页无实质内容，points 为空数组。
4. 识别本页所属章节名（如「一般人群膳食指南」「中国居民平衡膳食宝塔」），填入 section。
5. 若某条摄入量/建议仅适用于特定人群（孕妇、乳母、婴幼儿、儿童青少年、老年人、素食者、慢性病患者等），必须在 title 或 content 中明确写出该人群，严禁把特定亚人群的量当作一般人群推荐。

输出严格 JSON（键与字符串值都用双引号），不要任何额外文字或解释：
{"section":"<章节名>","points":[{"category":"<谷薯类|蔬菜水果|鱼禽肉蛋|奶豆坚果|油盐糖|饮水|运动体重|三餐规律|特殊人群|其他>","title":"<简短标题>","content":"<改写后的要点>","tags":["<关键词>"]}]}"""

# category 合法枚举（与抽取 prompt 一致）；落库时不在枚举内的归为「其他」
ALLOWED_CATEGORIES = {"谷薯类", "蔬菜水果", "鱼禽肉蛋", "奶豆坚果", "油盐糖",
                      "饮水", "运动体重", "三餐规律", "特殊人群", "其他"}


def http_post_json(url, payload, headers, timeout=90, retries=3):
    data = json.dumps(payload).encode("utf-8")
    last = None
    for attempt in range(1, retries + 1):
        try:
            req = urllib.request.Request(url, data=data, headers=headers, method="POST")
            with urllib.request.urlopen(req, timeout=timeout) as r:
                return json.loads(r.read().decode("utf-8"))
        except urllib.error.HTTPError as e:
            last = f"HTTP {e.code}: {e.read().decode('utf-8', 'ignore')[:300]}"
        except Exception as e:  # noqa
            last = f"{type(e).__name__}: {e}"
        # 限流（429）退避更久，给免费额度恢复时间
        backoff = 30 * attempt if "429" in last else 2 ** attempt
        print(f"    [retry {attempt}/{retries}] {last}; 等待 {backoff}s", flush=True)
        time.sleep(backoff)
    raise RuntimeError(f"请求失败: {last}")


def render_page_b64(doc, idx):
    page = doc[idx]
    zoom = PAGE_WIDTH / page.rect.width
    pix = page.get_pixmap(matrix=fitz.Matrix(zoom, zoom))
    buf = pix.tobytes("jpeg", jpg_quality=JPEG_QUALITY)
    return base64.b64encode(buf).decode("ascii")


def strip_fences(text):
    t = text.strip()
    if t.startswith("```"):
        t = t.strip("`")
        if t.lstrip().lower().startswith("json"):
            t = t[4:]
    return t.strip()


def parse_llm_json(text):
    """容错解析模型返回的 JSON：先标准解析，失败再退回 Python 字面量解析
    （模型偶发用单引号字符串如 '蔬菜'，json.loads 不认，但 ast.literal_eval 可解析）。"""
    s = strip_fences(text)
    try:
        obj = json.loads(s)
    except Exception:
        try:
            obj = ast.literal_eval(s)
        except Exception:
            return None
    if not isinstance(obj, dict):
        return None
    return obj


def extract_page(vision_key, vision_base, vision_model, page_idx, b64, use_json_mode=True):
    headers = {
        "Authorization": f"Bearer {vision_key}",
        "Content-Type": "application/json",
    }
    payload = {
        "model": vision_model,
        "messages": [
            {
                "role": "user",
                "content": [
                    {"type": "text", "text": EXTRACT_PROMPT},
                    {"type": "image_url", "image_url": {"url": f"data:image/jpeg;base64,{b64}"}},
                ],
            }
        ],
        "max_tokens": 1024,
        "temperature": 0.2,
    }
    if use_json_mode:
        payload["response_format"] = {"type": "json_object"}
    try:
        resp = http_post_json(vision_base, payload, headers, timeout=120)
    except Exception as ex:  # 限流/网络等导致整页失败：跳过而非中断整轮
        print(f"  [skip] 第{page_idx + 1}页抽取失败：{ex}", flush=True)
        return page_idx, {"section": "", "points": []}
    msg = resp.get("choices", [{}])[0].get("message", {})
    content = msg.get("content") or msg.get("reasoning_content") or ""
    obj = parse_llm_json(content)
    if obj is None:
        print(f"  [warn] 第{page_idx + 1}页 JSON 解析失败，原文前200字：{content[:200]}", flush=True)
        return page_idx, {"section": "", "points": []}
    points = obj.get("points") or []
    return page_idx, {"section": obj.get("section", ""), "points": points}


def embed_texts(zhipu_key, texts):
    if not zhipu_key:
        return [None] * len(texts)
    headers = {"Authorization": f"Bearer {zhipu_key}", "Content-Type": "application/json"}
    out = []
    for i in range(0, len(texts), 32):
        batch = texts[i:i + 32]
        payload = {"model": ZHIPU_MODEL, "input": batch}
        resp = http_post_json(ZHIPU_BASE + "/embeddings", payload, headers, timeout=60)
        by_index = {}
        for d in resp.get("data", []):
            by_index[d.get("index")] = d.get("embedding")
        for j in range(len(batch)):
            out.append(by_index.get(j))
    return out


def create_table_if_needed(conn):
    with conn.cursor() as cur:
        cur.execute("""
            CREATE TABLE IF NOT EXISTS guideline_chunks (
                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                page INT NOT NULL,
                section VARCHAR(120),
                category VARCHAR(40) NOT NULL,
                title VARCHAR(300) NOT NULL,
                content LONGTEXT NOT NULL,
                tags VARCHAR(400),
                source_name VARCHAR(120) NOT NULL,
                source_url VARCHAR(400),
                embedding_json LONGTEXT,
                enabled TINYINT(1) DEFAULT 1,
                created_at DATETIME,
                reviewed_at DATETIME
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
        """)


def insert_chunk(cur, chunk, now):
    cur.execute(
        """
        INSERT INTO guideline_chunks
            (page, section, category, title, content, tags, source_name, source_url, embedding_json, created_at, reviewed_at)
        VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
        """,
        (chunk["page"], chunk.get("section"), chunk["category"], chunk["title"], chunk["content"],
         ",".join(chunk.get("tags", [])), chunk["sourceName"], chunk["sourceUrl"],
         json.dumps(chunk["embedding"]) if chunk.get("embedding") else None, now, now),
    )


def main():
    global VISION_MODEL
    # 本地开发常用 MITM 代理（如 127.0.0.1:4780），其自签 CA 不被 certifi 信任；
    # 检测到 localhost 代理时关闭证书校验，仅用于本地构建脚本，不影响生产。
    proxy = os.environ.get("HTTPS_PROXY") or os.environ.get("https_proxy") or ""
    # 本地一次性构建脚本：放宽证书校验，兼容本机 Python 自带证书库缺 CA、
    # 以及 127.0.0.1 代理自签证书的情况。仅本地脚本使用，不影响生产。
    ssl._create_default_https_context = ssl._create_unverified_context
    ap = argparse.ArgumentParser()
    ap.add_argument("--pdf", required=False,
                    help="指南 PDF 路径（--from-json 模式下可省略）")
    ap.add_argument("--limit", type=int, default=0, help="只处理前 N 页（0=全量）")
    ap.add_argument("--out", default="scripts/guideline_chunks.json", help="输出 JSON 路径")
    ap.add_argument("--from-json", default=None, help="从已有 JSON 加载知识点做向量化/落库（跳过视觉抽取）")
    ap.add_argument("--db", action="store_true", help="直接写入 MySQL")
    ap.add_argument("--reset", action="store_true", help="落库前先清空 guideline_chunks，做幂等全量重建")
    ap.add_argument("--no-db", dest="db", action="store_false")
    ap.add_argument("--workers", type=int, default=3)
    ap.add_argument("--vision-provider", default="agnes", choices=["agnes", "zhipu"],
                    help="视觉抽取服务商：agnes(默认,需AGNES_API_KEY) 或 zhipu(需ZHIPU_API_KEY,glm-4v-plus)")
    ap.add_argument("--vision-model", default=VISION_MODEL)
    ap.add_argument("--source-name", default=SOURCE_NAME)
    ap.add_argument("--source-url", default=SOURCE_URL)
    ap.add_argument("--db-host", default="127.0.0.1")
    ap.add_argument("--db-port", type=int, default=3306)
    ap.add_argument("--db-user", default="root")
    ap.add_argument("--db-pass", default="12345678")
    ap.add_argument("--db-name", default="mood_recipe")
    args = ap.parse_args()

    if not args.from_json and not args.pdf:
        sys.exit("--pdf 必填（除非使用 --from-json 从已有 JSON 加载）")

    VISION_MODEL = args.vision_model
    agnes_key = os.environ.get("AGNES_API_KEY", "")
    zhipu_key = os.environ.get("ZHIPU_API_KEY", "")
    if args.vision_provider == "zhipu":
        # 智谱视觉+向量共用 ZHIPU_API_KEY；视觉走 glm-4v-plus 聊天端点
        if not VISION_MODEL or VISION_MODEL == "agnes-2.5-flash":
            VISION_MODEL = "glm-4v-flash"
        vision_base = ZHIPU_BASE + "/chat/completions"
        vision_key = zhipu_key
        use_json_mode = False  # glm-4v-plus 不强制 json_object，靠 prompt + strip_fences 兜底
        if not vision_key:
            sys.exit("缺少 ZHIPU_API_KEY 环境变量（智谱视觉抽取需要）")
    else:
        vision_base = AGNES_BASE
        vision_key = agnes_key
        use_json_mode = True
        if not args.from_json and not vision_key:
            sys.exit("缺少 AGNES_API_KEY 环境变量（agnes 网关视觉抽取需要）")
    if not zhipu_key:
        print("[warn] 未设置 ZHIPU_API_KEY，将跳过向量化（仅生成无 embedding 的知识点 JSON）", flush=True)

    chunks = []
    if args.from_json:
        print(f"从已有 JSON 加载知识点：{args.from_json}", flush=True)
        with open(args.from_json, encoding="utf-8") as f:
            chunks = json.load(f)
        print(f"共加载 {len(chunks)} 条知识点", flush=True)
    else:
        print(f"打开 PDF：{args.pdf}", flush=True)
        doc = fitz.open(args.pdf)
        total = doc.page_count
        limit = args.limit if 0 < args.limit < total else total
        print(f"共 {total} 页，本次处理 {limit} 页", flush=True)

        # 1) 渲染 + 视觉抽取
        print("阶段1：渲染 + 视觉抽取知识点 ...", flush=True)
        pages_b64 = [render_page_b64(doc, i) for i in range(limit)]
        results = {}
        t0 = time.time()
        with ThreadPoolExecutor(max_workers=max(1, args.workers)) as ex:
            futs = {ex.submit(extract_page, vision_key, vision_base, VISION_MODEL, i, pages_b64[i], use_json_mode): i
                    for i in range(limit)}
            done = 0
            for fut in as_completed(futs):
                idx, obj = fut.result()
                results[idx] = obj
                done += 1
                if done % 10 == 0 or done == limit:
                    print(f"  已抽取 {done}/{limit} 页 ({int(time.time() - t0)}s)", flush=True)

        # 2) 聚合成知识点
        for idx in range(limit):
            obj = results.get(idx, {"section": "", "points": []})
            for p in obj.get("points", []):
                cat = p.get("category") or "其他"
                title = (p.get("title") or "").strip()
                content = (p.get("content") or "").strip()
                tags = p.get("tags") or []
                if not content:
                    continue
                chunks.append({
                    "page": idx + 1,
                    "section": obj.get("section", ""),
                    "category": cat,
                    "title": title,
                    "content": content,
                    "tags": tags,
                    "sourceName": args.source_name,
                    "sourceUrl": args.source_url,
                    "embedding": None,
                })
        print(f"共抽取 {len(chunks)} 条知识点", flush=True)

    # 3) 向量化
    if zhipu_key and chunks:
        print("阶段2：智谱 embedding 向量化 ...", flush=True)
        texts = [f"{c['title']} {c['content']} {' '.join(c['tags'])}" for c in chunks]
        vecs = embed_texts(zhipu_key, texts)
        ok = 0
        for c, v in zip(chunks, vecs):
            if v:
                c["embedding"] = v
                ok += 1
        print(f"向量化完成 {ok}/{len(chunks)} 条", flush=True)
    else:
        print("阶段2：跳过向量化", flush=True)

    # 4) 输出 JSON
    os.makedirs(os.path.dirname(os.path.abspath(args.out)), exist_ok=True)
    with open(args.out, "w", encoding="utf-8") as f:
        json.dump(chunks, f, ensure_ascii=False, indent=2)
    print(f"已写出 JSON：{args.out}", flush=True)

    # 5) 落库（可选）
    if args.db:
        if pymysql is None:
            sys.exit("需要 pymysql：python3 -m pip install --user pymysql")
        print(f"写入 MySQL {args.db_host}/{args.db_name} ...", flush=True)
        conn = pymysql.connect(host=args.db_host, port=args.db_port, user=args.db_user,
                               password=args.db_pass, database=args.db_name,
                               charset="utf8mb4")
        try:
            create_table_if_needed(conn)
            if args.reset:
                with conn.cursor() as cur:
                    cur.execute("DELETE FROM guideline_chunks")
                conn.commit()
                print("  [reset] 已清空 guideline_chunks，全量重建", flush=True)
            now = time.strftime("%Y-%m-%d %H:%M:%S")
            # 落库前清洗：category 必须落在枚举内，过长字段截断到列上限，避免插入崩溃
            for c in chunks:
                cat = (c.get("category") or "").strip()
                c["category"] = cat if cat in ALLOWED_CATEGORIES else "其他"
                c["section"] = (c.get("section") or "")[:120]
                c["title"] = (c.get("title") or "")[:300]
                c["tags"] = (c.get("tags") or "")[:400]
            n = 0
            with conn.cursor() as cur:
                for c in chunks:
                    if not c.get("embedding"):
                        print(f"  [skip] 第{c['page']}页「{c['title']}」无向量，跳过入库", flush=True)
                        continue
                    insert_chunk(cur, c, now)
                    n += 1
            conn.commit()
            print(f"已入库 {n} 条", flush=True)
        finally:
            conn.close()
    print("完成。", flush=True)


if __name__ == "__main__":
    main()
