package com.moodrecipe.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moodrecipe.backend.entity.DailyMenu;
import com.moodrecipe.backend.entity.Recipe;
import com.moodrecipe.backend.repository.DailyMenuRepository;
import com.moodrecipe.backend.repository.RecipeInteractionRepository;
import com.moodrecipe.backend.repository.RecipeFavoriteRepository;
import com.moodrecipe.backend.repository.RecipeRepository;
import com.moodrecipe.backend.repository.UserFoodPreferenceRepository;
import com.moodrecipe.backend.repository.UserRecordRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DailyMenuServiceTest {

    @Test
    void doesNotReturnCachedRecipeWithQuestionMarkCorruption() {
        DailyMenuRepository dailyMenus = mock(DailyMenuRepository.class);
        RecipeRepository recipes = mock(RecipeRepository.class);
        DailyMenu cached = new DailyMenu();
        cached.setOpenid("user-1");
        cached.setMenuDate(LocalDate.now());
        cached.setRecipeId(9L);
        cached.setGuozaiLine("锅仔今天给你留了 ?????");
        when(dailyMenus.findByOpenidAndMenuDate(any(), any())).thenReturn(Optional.of(cached));

        Recipe broken = new Recipe();
        broken.setId(9L);
        broken.setName("?????");
        broken.setDescription("今晚 ?????");
        when(recipes.findById(9L)).thenReturn(Optional.of(broken));
        when(recipes.findAiWithImages()).thenReturn(java.util.List.of());
        when(recipes.findAll()).thenReturn(java.util.List.of());

        DailyMenuService service = new DailyMenuService(dailyMenus, recipes,
                mock(RecipeInteractionRepository.class), mock(UserRecordRepository.class),
                mock(UserFoodPreferenceRepository.class),
                new AllergenNormalizationService(null, new ObjectMapper()), new RecipePool(recipes), mock(RecipeFavoriteRepository.class));

        assertTrue(service.today("user-1").isEmpty());
    }

    /** 昨日今日卡推过的菜（即使它是收藏+做过的高分菜），今天不得再出现——防「连续 N 天同一道」回归。 */
    @Test
    void doesNotRepeatYesterdayBoardDishEvenIfTopScored() {
        DailyMenuRepository dailyMenus = mock(DailyMenuRepository.class);
        RecipeRepository recipes = mock(RecipeRepository.class);
        RecipeInteractionRepository interactions = mock(RecipeInteractionRepository.class);
        when(dailyMenus.findByOpenidAndMenuDate(any(), any())).thenReturn(Optional.empty());
        DailyMenu yesterday = new DailyMenu();
        yesterday.setOpenid("user-1");
        yesterday.setMenuDate(LocalDate.now().minusDays(1));
        yesterday.setRecipeId(1L);
        when(dailyMenus.findByOpenidAndMenuDateGreaterThanEqual(any(), any()))
                .thenReturn(java.util.List.of(yesterday));

        Recipe topScored = new Recipe();
        topScored.setId(1L);
        topScored.setName("糖醋里脊");
        topScored.setDescription("经典家常菜");
        Recipe alternative = new Recipe();
        alternative.setId(2L);
        alternative.setName("番茄炒蛋");
        alternative.setDescription("家常快手菜");
        when(recipes.findAiWithImages()).thenReturn(java.util.List.of(topScored, alternative));
        when(recipes.findById(2L)).thenReturn(Optional.of(alternative));
        when(interactions.findByOpenidAndAction(any(), any())).thenReturn(java.util.List.of());
        // 做过 +5：没有窗口去重时它必然赢
        com.moodrecipe.backend.entity.RecipeInteraction made = new com.moodrecipe.backend.entity.RecipeInteraction();
        made.setOpenid("user-1");
        made.setRecipeId(1L);
        made.setAction("MADE");
        when(interactions.findTop30ByOpenidOrderByCreatedAtDesc(any())).thenReturn(java.util.List.of(made));

        DailyMenuService service = new DailyMenuService(dailyMenus, recipes,
                interactions, mock(UserRecordRepository.class),
                mock(UserFoodPreferenceRepository.class),
                new AllergenNormalizationService(null, new ObjectMapper()), new RecipePool(recipes), mock(RecipeFavoriteRepository.class));

        assertTrue(service.today("user-1").map(board -> board.recipe().getId() == 2L).orElse(false));
    }

    /** 餐别窗口：早 5-10 / 午 10-16 / 晚 16-22 / 其余夜宵。 */
    @Test
    void mealWindowMapsDayHoursCorrectly() {
        assertEquals("BREAKFAST", DailyMenuService.mealTypeOf(java.time.LocalDateTime.of(2026, 10, 17, 7, 30)));
        assertEquals("BREAKFAST", DailyMenuService.mealTypeOf(java.time.LocalDateTime.of(2026, 10, 17, 5, 0)));
        assertEquals("LUNCH", DailyMenuService.mealTypeOf(java.time.LocalDateTime.of(2026, 10, 17, 12, 0)));
        assertEquals("DINNER", DailyMenuService.mealTypeOf(java.time.LocalDateTime.of(2026, 10, 17, 19, 0)));
        assertEquals("LATE", DailyMenuService.mealTypeOf(java.time.LocalDateTime.of(2026, 10, 17, 23, 30)));
        assertEquals("LATE", DailyMenuService.mealTypeOf(java.time.LocalDateTime.of(2026, 10, 17, 2, 0)));
    }

    /** 时段智能：早上早餐组合压过重口炒菜，晚上清淡蒸菜压过重口——软偏置行为契约。 */
    @Test
    void mealBonusPrefersBreakfastInTheMorningAndLightFoodAtNight() {
        Recipe breakfast = new Recipe();
        breakfast.setId(1L);
        breakfast.setName("皮蛋瘦肉粥");
        breakfast.setMoodTags("早餐");
        Recipe heavyStirFry = new Recipe();
        heavyStirFry.setId(2L);
        heavyStirFry.setName("辣子鸡");
        Recipe lightSteamed = new Recipe();
        lightSteamed.setId(3L);
        lightSteamed.setName("清蒸鲈鱼");
        lightSteamed.setTaste("清淡");

        assertTrue(DailyMenuService.mealBonus(breakfast, "BREAKFAST") >= 8);
        assertTrue(DailyMenuService.mealBonus(heavyStirFry, "BREAKFAST") <= -6);
        assertTrue(DailyMenuService.mealBonus(breakfast, "BREAKFAST") > DailyMenuService.mealBonus(heavyStirFry, "BREAKFAST"));
        assertTrue(DailyMenuService.mealBonus(lightSteamed, "DINNER") > DailyMenuService.mealBonus(heavyStirFry, "DINNER"));
    }
}
