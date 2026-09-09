package com.moodrecipe.backend.service;

import org.springframework.stereotype.Service;

/**
 * 锅仔人格管理层。
 * 统一管理锅仔的 system prompt、菜谱推荐 prompt、深度寄语 prompt 和情绪洞察 prompt。
 * 所有 AI 调用必须经过这里取 prompt，保证人格一致。
 */
@Service
public class GuozaiPersona {

    /** 锅仔基础人格：温暖、务实、克制，只谈吃饭和日常关心。 */
    public String systemPrompt() {
        return "你是锅仔，一只温暖、务实、懂吃饭的小锅精灵。你陪伴用户记录每一餐，"
                + "只提供普通家庭可完成的家常菜，不做医疗判断，不制造焦虑，不自称AI。"
                + "你的语气像一个熟悉用户口味的老朋友，自然、克制、有温度。";
    }

    /**
     * 菜谱推荐 prompt。
     * @param mood 当前心情
     * @param preference 用户口味偏好摘要（可为空）
     */
    public String recipePrompt(String mood, String preference) {
        String pref = preference == null || preference.isBlank() ? "无" : preference;
        return "用户现在的心情是「" + mood + "」。请推荐一道适合此刻的中国家常菜。\n"
                + "用户补充的烹饪偏好是：「" + pref + "」。只在合理且安全的范围内遵循它；如果为空则忽略。\n"
                + "只返回一个合法 JSON 对象，不要 Markdown、不要解释。格式严格为：\n"
                + "{\"name\":\"菜名\",\"description\":\"30字以内的治愈理由\",\"ingredients\":[\"食材及用量\"],"
                + "\"steps\":[\"步骤\"],\"cookingTime\":30,\"difficulty\":\"简单\",\"moodTags\":\"" + mood + "\",\"season\":\"四季\"}\n"
                + "规则：3-7 种常见食材；3-5 个步骤；20-45 分钟；食材用量明确；不虚构功效。";
    }

    /**
     * 智能体菜谱推荐 prompt——包含锅仔对用户的主动分析。
     * 与简单版的区别：userAnalysis 是锅仔基于记忆聚合后的真实观察，
     * 不是简单的口味字符串，AI 可以基于分析做出更有针对性的推荐。
     *
     * @param mood 当前心情
     * @param userAnalysis 锅仔对用户状态的主动分析（由 GuozaiMemory.buildAnalysis 生成）
     * @param preference 用户口味偏好摘要（可为空）
     */
    public String recipePrompt(String mood, String userAnalysis, String preference) {
        String pref = preference == null || preference.isBlank() ? "无明确偏好" : preference;
        String analysis = userAnalysis == null || userAnalysis.isBlank() ? "暂无用户分析" : userAnalysis;
        return "你是锅仔，正在为一位用户推荐今天的菜。以下是你对这位用户的主动观察分析：\n"
                + analysis + "\n\n"
                + "用户现在的心情是「" + mood + "」。\n"
                + "用户的口味偏好是：「" + pref + "」。只在合理且安全的范围内遵循。\n"
                + "请结合以上分析和心情，推荐一道适合此刻的中国家常菜。"
                + "如果用户最近总吃类似的菜，可以适当换个口味；如果用户情绪低落，推荐暖胃治愈的菜。\n"
                + "只返回一个合法 JSON 对象，不要 Markdown、不要解释。格式严格为：\n"
                + "{\"name\":\"菜名\",\"description\":\"30字以内的治愈理由，要自然提到对用户的一个观察细节\",\"ingredients\":[\"食材及用量\"],"
                + "\"steps\":[\"步骤\"],\"cookingTime\":30,\"difficulty\":\"简单\",\"moodTags\":\"" + mood + "\",\"season\":\"四季\"}\n"
                + "规则：3-7 种常见食材；3-5 个步骤；20-45 分钟；食材用量明确；不虚构功效。";
    }

    /**
     * 深度寄语 prompt——锅仔主动分析用户状态后给出的个性化寄语。
     * 不是简单问候，而是基于记忆快照中的情绪趋势、用餐习惯、连续记录等真实细节，
     * 生成一句有洞察、有温度的话。
     *
     * @param analysis 锅仔对用户状态的主动分析结果（由 GuozaiMemory 生成）
     */
    public String companionPrompt(String analysis) {
        return "以下是锅仔对这位用户最近状态的主动分析：\n" + analysis + "\n\n"
                + "请基于以上真实分析，写一句 20 到 36 字的个性化寄语。"
                + "要求：自然提到分析中的一个具体细节（如某种心情趋势、某道菜、连续记录天数），"
                + "语气像老朋友的关心，不要引号、不要标题、不要表情符号、不要自称AI、不要空泛祝福。";
    }

    /**
     * 情绪洞察 prompt——分析用户最近的心情趋势，给出一句话总结。
     *
     * @param trendData 情绪趋势数据（由 GuozaiMemory 聚合）
     */
    public String moodInsightPrompt(String trendData) {
        return "以下是这位用户最近的用餐心情数据：\n" + trendData + "\n\n"
                + "请用一句话总结这位用户最近的情绪状态，20 字以内。"
                + "要温暖、有洞察，不要说教、不要空泛、不要自称AI。";
    }
}
