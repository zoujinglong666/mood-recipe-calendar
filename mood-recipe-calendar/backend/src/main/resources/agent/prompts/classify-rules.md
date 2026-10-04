请判断用户这句话属于哪一类，只输出一个 JSON 对象：
{"intent":"ASK_KNOWLEDGE|MEAL_INFO","needWeb":true/false,"reason":""}
判定规则：
- ASK_KNOWLEDGE：用户在问知识或求建议，例如"最近什么菜应季""西红柿怎么挑""红烧肉怎么做才不腻""XX 有什么营养"。这类要回答问题，不是提供备餐信息。
- MEAL_INFO：用户在提供自己的用餐信息或做选择，例如"3 个人吃""能吃辣""周五周六做饭""想吃得清淡""换一道"。
- 打招呼、闲聊、问你是谁，也算 ASK_KNOWLEDGE。
- 在 ASK_KNOWLEDGE 时判断 needWeb：涉及实时/库外/时效性信息（应季、最新、行情、通用生活常识）为 true；仅凭常识或菜谱库能答的为 false。
- 拿不准时倾向 MEAL_INFO（宁可继续问卷，也不要抢答）。
