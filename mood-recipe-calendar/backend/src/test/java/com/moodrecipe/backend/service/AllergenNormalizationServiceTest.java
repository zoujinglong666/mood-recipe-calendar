package com.moodrecipe.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moodrecipe.backend.agent.LlmClient;
import com.moodrecipe.backend.agent.LlmResponse;
import com.moodrecipe.backend.agent.LlmResult;
import com.moodrecipe.backend.agent.LlmUsage;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AllergenNormalizationServiceTest {

    @Test
    void combinesLocalSafetyTermsWithModelSynonyms() {
        LlmClient llm = mock(LlmClient.class);
        when(llm.isConfigured()).thenReturn(true);
        when(llm.complete(any())).thenReturn(LlmResult.ok(new LlmResponse(
                "{\"terms\":[\"虾仁\",\"海虾\"]}", List.of(), "stop", new LlmUsage(0, 0, 0), "test"), 1, 0));

        List<String> result = new AllergenNormalizationService(llm, new ObjectMapper())
                .normalize("", "对虾过敏");

        // 本地类目展开全族在前，模型同义词增量在后
        assertEquals(List.of("对虾", "虾", "虾仁", "虾皮", "虾米", "基围虾", "明虾", "龙虾", "海虾"), result);
    }

    @Test
    void keepsLocalTermsWhenModelIsUnavailable() {
        LlmClient llm = mock(LlmClient.class);
        when(llm.isConfigured()).thenReturn(false);

        List<String> result = new AllergenNormalizationService(llm, new ObjectMapper())
                .normalize("", "花生过敏");

        assertEquals(List.of("花生"), result);
    }

    @Test
    void keepsShrimpSynonymWhenModelIsUnavailable() {
        LlmClient llm = mock(LlmClient.class);
        when(llm.isConfigured()).thenReturn(false);

        List<String> result = new AllergenNormalizationService(llm, new ObjectMapper())
                .normalize("", "对虾过敏");

        assertEquals(List.of("对虾", "虾", "虾仁", "虾皮", "虾米", "基围虾", "明虾", "龙虾"), result);
    }

    @Test
    void expandsSeafoodCategoryLocallyWhenModelIsUnavailable() {
        LlmClient llm = mock(LlmClient.class);
        when(llm.isConfigured()).thenReturn(false);

        List<String> result = new AllergenNormalizationService(llm, new ObjectMapper())
                .normalize("", "海鲜过敏");

        assertEquals(List.of("海鲜", "虾", "虾仁", "虾皮", "虾米", "蟹", "螃蟹", "贝", "蛤", "蛏", "牡蛎", "扇贝", "鱿鱼", "章鱼", "墨鱼", "鱼"), result);
    }

    @Test
    void expandsDairyCategoryAndStripsCategorySuffix() {
        LlmClient llm = mock(LlmClient.class);
        when(llm.isConfigured()).thenReturn(false);

        List<String> result = new AllergenNormalizationService(llm, new ObjectMapper())
                .normalize("乳制品不耐受", "");

        assertEquals(List.of("乳制品", "奶", "牛奶", "奶粉", "奶油", "奶酪", "芝士", "乳酪", "黄油", "酸奶", "炼乳"), result);
    }

    @Test
    void expandsNutCategoryWithLocalTermsFirst() {
        LlmClient llm = mock(LlmClient.class);
        when(llm.isConfigured()).thenReturn(true);
        when(llm.complete(any())).thenReturn(LlmResult.ok(new LlmResponse(
                "not-json", List.of(), "stop", new LlmUsage(0, 0, 0), "test"), 1, 0));

        List<String> result = new AllergenNormalizationService(llm, new ObjectMapper())
                .normalize("", "坚果过敏");

        // 模型返回非法 JSON 也不影响本地安全展开
        assertEquals(List.of("坚果", "花生", "核桃", "腰果", "杏仁", "榛子", "开心果", "夏威夷果", "松子", "板栗"), result);
    }

    @Test
    void keepsLocalTermsWhenModelReturnsInvalidJson() {
        LlmClient llm = mock(LlmClient.class);
        when(llm.isConfigured()).thenReturn(true);
        when(llm.complete(any())).thenReturn(LlmResult.ok(new LlmResponse(
                "not-json", List.of(), "stop", new LlmUsage(0, 0, 0), "test"), 1, 0));

        List<String> result = new AllergenNormalizationService(llm, new ObjectMapper())
                .normalize("", "花生过敏");

        assertEquals(List.of("花生"), result);
    }
}
