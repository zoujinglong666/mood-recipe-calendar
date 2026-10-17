package com.moodrecipe.backend.service;

import com.moodrecipe.backend.entity.Recipe;
import com.moodrecipe.backend.repository.RecipeRepository;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 视觉质检回环编排：通过即用、不合格带意见重拍一次、复检不过宁缺毋滥。
 */
class RecipeCoverBackfillServiceTest {

    private final AgnesRecipeImageService images = mock(AgnesRecipeImageService.class);
    private final CosImageStorageService cos = mock(CosImageStorageService.class);
    private final RecipeRepository recipes = mock(RecipeRepository.class);
    private final ImageQualityGateService gate = mock(ImageQualityGateService.class);
    private final RecipeCoverBackfillService service = new RecipeCoverBackfillService(images, cos, recipes, gate);

    private Recipe recipe(String name) {
        Recipe r = new Recipe();
        r.setName(name);
        return r;
    }

    @Test
    void firstPassUsesCoverDirectly() {
        Recipe r = recipe("番茄牛腩");
        when(images.generateCover(r)).thenReturn(Optional.of("https://agnes/raw1.png"));
        when(cos.transferFromUrl("https://agnes/raw1.png", "recipes/")).thenReturn(Optional.of("https://cos/1.png"));
        when(gate.review("https://cos/1.png", "番茄牛腩", false))
                .thenReturn(new ImageQualityGateService.Verdict(true, ""));

        assertEquals("https://cos/1.png", service.obtainCover(r).orElseThrow());
    }

    @Test
    void rejectedCoverRetriesOnceWithCritique() {
        Recipe r = recipe("鲜肉馄饨 · 配米糊");
        when(images.generateCover(r)).thenReturn(Optional.of("https://agnes/bad.png"));
        when(cos.transferFromUrl("https://agnes/bad.png", "recipes/")).thenReturn(Optional.of("https://cos/bad.png"));
        when(gate.review("https://cos/bad.png", "鲜肉馄饨 · 配米糊", true))
                .thenReturn(new ImageQualityGateService.Verdict(false, "生肉块且两碗混装"));
        // 重拍：必须带质检意见且绕过缓存
        when(images.generateCover(eq(r), contains("生肉块"), eq(false))).thenReturn(Optional.of("https://agnes/good.png"));
        when(cos.transferFromUrl("https://agnes/good.png", "recipes/")).thenReturn(Optional.of("https://cos/good.png"));
        when(gate.review("https://cos/good.png", "鲜肉馄饨 · 配米糊", true))
                .thenReturn(new ImageQualityGateService.Verdict(true, ""));

        Optional<String> result = service.obtainCover(r);

        assertEquals("https://cos/good.png", result.orElseThrow());
        // 顺序约束：先生成→转存→质检，重拍在质检之后
        InOrder order = inOrder(images, gate);
        order.verify(images).generateCover(r);
        order.verify(gate).review("https://cos/bad.png", "鲜肉馄饨 · 配米糊", true);
        order.verify(images).generateCover(eq(r), contains("生肉块"), eq(false));
        order.verify(gate).review("https://cos/good.png", "鲜肉馄饨 · 配米糊", true);
    }

    @Test
    void doubleRejectionGivesUpInsteadOfSavingBadCover() {
        Recipe r = recipe("鲜肉馄饨");
        when(images.generateCover(r)).thenReturn(Optional.of("https://agnes/bad.png"));
        when(images.generateCover(eq(r), anyString(), org.mockito.ArgumentMatchers.anyBoolean()))
                .thenReturn(Optional.of("https://agnes/bad2.png"));
        when(cos.transferFromUrl(anyString(), anyString())).thenAnswer(inv ->
                Optional.of(((String) inv.getArgument(0)).replace("agnes", "cos")));
        when(gate.review(anyString(), anyString(), org.mockito.ArgumentMatchers.anyBoolean()))
                .thenReturn(new ImageQualityGateService.Verdict(false, "仍然有生肉"));

        assertTrue(service.obtainCover(r).isEmpty(), "复检仍不合格必须返回 empty，坏图绝不回写");
        // 两次生成、两次质检都要发生
        org.mockito.Mockito.verify(images).generateCover(r);
        org.mockito.Mockito.verify(images).generateCover(eq(r), anyString(), eq(false));
        org.mockito.Mockito.verify(gate, org.mockito.Mockito.times(2)).review(anyString(), eq("鲜肉馄饨"), eq(false));
    }

    @Test
    void comboNameTriggersComboReviewCriteria() {
        Recipe r = recipe("鲜虾肠粉 · 配牛奶");
        when(images.generateCover(r)).thenReturn(Optional.of("https://agnes/a.png"));
        when(cos.transferFromUrl(anyString(), anyString())).thenReturn(Optional.of("https://cos/a.png"));
        when(gate.review("https://cos/a.png", "鲜虾肠粉 · 配牛奶", true))
                .thenReturn(new ImageQualityGateService.Verdict(true, ""));

        service.obtainCover(r);

        // 组合菜名必须以 combo=true 送检（校验两碗分摆）
        org.mockito.Mockito.verify(gate).review("https://cos/a.png", "鲜虾肠粉 · 配牛奶", true);
    }
}
