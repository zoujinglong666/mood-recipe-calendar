-- 调味时序知识入库（做菜智能体 RAG）：回答「什么时候放调料」；只收公认原理，不收相克谣言。
-- 幂等：按 title 去重，重复执行不产生重复行。

INSERT INTO cooking_knowledge_chunks
  (title, content, category, keywords, source_name, source_url, source_version, reviewed_at, enabled, created_at, updated_at)
SELECT '盐什么时候放', '炒菜出锅前 30 秒再放盐，炖菜最后 10 分钟放。盐渗透最快，早放会把食材细胞里的水抽出来：肉变柴、青菜出水发塌、汤越炖越咸。腌肉例外——腌制阶段放盐是正常工艺，能让肉汁形成凝胶外焦里嫩。',
       'SEASONING', '盐,什么时候放盐,咸,柴,脱水,炖,炒,腌肉', '锅仔调味时序知识库（整理自公认烹饪原理）', 'https://moodrecipe.icu', '2026-10', NOW(), 1, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM cooking_knowledge_chunks WHERE title='盐什么时候放');

INSERT INTO cooking_knowledge_chunks
  (title, content, category, keywords, source_name, source_url, source_version, reviewed_at, enabled, created_at, updated_at)
SELECT '糖什么时候放', '糖要早放（炒制上色阶段）。糖渗透慢，需要时间进入食材上色出焦香；先糖后盐，互不打架。炒糖色则在油温低时下糖小火慢炒。',
       'SEASONING', '糖,白糖,冰糖,糖色,上色,焦香,什么时候放糖', '锅仔调味时序知识库（整理自公认烹饪原理）', 'https://moodrecipe.icu', '2026-10', NOW(), 1, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM cooking_knowledge_chunks WHERE title='糖什么时候放');

INSERT INTO cooking_knowledge_chunks
  (title, content, category, keywords, source_name, source_url, source_version, reviewed_at, enabled, created_at, updated_at)
SELECT '料酒什么时候放', '锅温最高的时候沿锅边淋一圈。酒精瞬间挥发带走腥味、留下香气；锅不热料酒味会闷在菜里。焯水阶段加料酒姜葱也是同理去腥。',
       'SEASONING', '料酒,去腥,锅边,酒精,什么时候放料酒', '锅仔调味时序知识库（整理自公认烹饪原理）', 'https://moodrecipe.icu', '2026-10', NOW(), 1, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM cooking_knowledge_chunks WHERE title='料酒什么时候放');

INSERT INTO cooking_knowledge_chunks
  (title, content, category, keywords, source_name, source_url, source_version, reviewed_at, enabled, created_at, updated_at)
SELECT '醋什么时候放', '出锅前沿锅边烹。醋香易挥发，早放只剩酸味没有醋香；凉拌菜最后拌入同理。需要软化食材的（如土豆丝）可中途加一次、出锅再补一次。',
       'SEASONING', '醋,米醋,香醋,陈醋,酸,什么时候放醋,锅边', '锅仔调味时序知识库（整理自公认烹饪原理）', 'https://moodrecipe.icu', '2026-10', NOW(), 1, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM cooking_knowledge_chunks WHERE title='醋什么时候放');

INSERT INTO cooking_knowledge_chunks
  (title, content, category, keywords, source_name, source_url, source_version, reviewed_at, enabled, created_at, updated_at)
SELECT '生抽老抽什么时候放', '生抽管鲜、老抽管色。生抽中段放（炝锅、料酒之后），鲜味需要温度激发但不宜久烧；老抽早放或炖煮中途放，颜色需要时间渗进食材。经典配比约 2:1。',
       'SEASONING', '生抽,老抽,酱油,上色,提鲜,什么时候放酱油', '锅仔调味时序知识库（整理自公认烹饪原理）', 'https://moodrecipe.icu', '2026-10', NOW(), 1, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM cooking_knowledge_chunks WHERE title='生抽老抽什么时候放');

INSERT INTO cooking_knowledge_chunks
  (title, content, category, keywords, source_name, source_url, source_version, reviewed_at, enabled, created_at, updated_at)
SELECT '味精鸡精什么时候放', '关火前放。味精的主要成分谷氨酸钠超过 120°C 长时间加热会分解成没有鲜味的焦谷氨酸钠；另外放了醋的菜不建议加味精——酸性环境溶解度低，鲜味出不来。味精本身安全，致癌是谣言。',
       'SEASONING', '味精,鸡精,鲜,谷氨酸钠,出锅前,什么时候放味精', '锅仔调味时序知识库（整理自权威科普，已核谣言）', 'https://moodrecipe.icu', '2026-10', NOW(), 1, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM cooking_knowledge_chunks WHERE title='味精鸡精什么时候放');

INSERT INTO cooking_knowledge_chunks
  (title, content, category, keywords, source_name, source_url, source_version, reviewed_at, enabled, created_at, updated_at)
SELECT '香油蚝油什么时候放', '都关火后再加。香油的香气全靠挥发，下锅早等于白放；蚝油久煮鲜味打折，临出锅加才有效。',
       'SEASONING', '香油,麻油,蚝油,提香,出锅,什么时候放香油', '锅仔调味时序知识库（整理自公认烹饪原理）', 'https://moodrecipe.icu', '2026-10', NOW(), 1, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM cooking_knowledge_chunks WHERE title='香油蚝油什么时候放');

INSERT INTO cooking_knowledge_chunks
  (title, content, category, keywords, source_name, source_url, source_version, reviewed_at, enabled, created_at, updated_at)
SELECT '香辛料什么时候下锅', '八角、桂皮质地硬、香味浓，与食材一起下锅久炖才出醇厚底香，过量会发苦；香叶属中段释放，炖煮中途放；花椒、干辣椒热油小火炝锅，火大易糊发苦。',
       'SEASONING', '八角,桂皮,香叶,花椒,干辣椒,香料,炝锅,什么时候放香料', '锅仔调味时序知识库（整理自公认烹饪原理）', 'https://moodrecipe.icu', '2026-10', NOW(), 1, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM cooking_knowledge_chunks WHERE title='香辛料什么时候下锅');

INSERT INTO cooking_knowledge_chunks
  (title, content, category, keywords, source_name, source_url, source_version, reviewed_at, enabled, created_at, updated_at)
SELECT '葱姜蒜什么时候放', '姜最耐高温，炝锅时与油同下打底去腥；葱分两头——炝锅用葱白、出锅撒葱花；蒜分两次——炝锅用一半蒜末，出锅前撒另一半，生蒜香是最后点睛。',
       'SEASONING', '葱,姜,蒜,蒜末,葱花,炝锅,爆香,什么时候放蒜', '锅仔调味时序知识库（整理自公认烹饪原理）', 'https://moodrecipe.icu', '2026-10', NOW(), 1, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM cooking_knowledge_chunks WHERE title='葱姜蒜什么时候放');

INSERT INTO cooking_knowledge_chunks
  (title, content, category, keywords, source_name, source_url, source_version, reviewed_at, enabled, created_at, updated_at)
SELECT '水淀粉什么时候放', '出锅前勾芡。早放糊锅底，久煮芡会澥；勾芡前先把水淀粉搅匀，沿锅边淋入再快速翻匀。',
       'SEASONING', '水淀粉,勾芡,淀粉,浓稠,什么时候勾芡', '锅仔调味时序知识库（整理自公认烹饪原理）', 'https://moodrecipe.icu', '2026-10', NOW(), 1, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM cooking_knowledge_chunks WHERE title='水淀粉什么时候放');

INSERT INTO cooking_knowledge_chunks
  (title, content, category, keywords, source_name, source_url, source_version, reviewed_at, enabled, created_at, updated_at)
SELECT '炒素菜调味口诀', '绿叶菜全程不放盐直到出锅前——早放盐细胞脱水、出水发塌；出锅前沿锅边一点醋能保翠绿。口诀：热锅热油快炒，盐和醋都在最后。',
       'SEASONING', '素菜,青菜,绿叶菜,炒菜,出水,翠绿,口诀', '锅仔调味时序知识库（整理自公认烹饪原理）', 'https://moodrecipe.icu', '2026-10', NOW(), 1, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM cooking_knowledge_chunks WHERE title='炒素菜调味口诀');

INSERT INTO cooking_knowledge_chunks
  (title, content, category, keywords, source_name, source_url, source_version, reviewed_at, enabled, created_at, updated_at)
SELECT '炖菜调味口诀', '先上色后调味：焯水去腥（加料酒姜）→ 炒糖色或老抽上色 → 加开水没过食材 → 八角桂皮同炖 → 中途放生抽冰糖 → 盐最后 10 分钟。炖煮中途加水要加热水；盐放早了肉柴汤咸。',
       'SEASONING', '炖,红烧,卤,上色,开水,口诀,什么时候放盐', '锅仔调味时序知识库（整理自公认烹饪原理）', 'https://moodrecipe.icu', '2026-10', NOW(), 1, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM cooking_knowledge_chunks WHERE title='炖菜调味口诀');
