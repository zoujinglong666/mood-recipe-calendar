package com.moodrecipe.backend.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moodrecipe.backend.entity.Recipe;
import com.moodrecipe.backend.repository.RecipeRepository;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 菜谱目录补齐逻辑已于 2026-09-15 停用（见 RecipeCatalogSeeder 类注释），
 * 其依赖的 recipe-catalog-v2.psv 也未随版本发布，因此这两个用例暂时挂起。
 * 若日后恢复本地种子目录，需要同时恢复资源文件并去掉本注解。
 */
@Disabled("本地菜谱种子目录已停用，recipe-catalog-v2.psv 未随版本发布")
class RecipeCatalogSeederTest {
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void bundledCatalogHasTwoHundredExecutableUniqueRecipes() throws Exception {
        List<RecipeCatalogSeeder.CatalogItem> items = RecipeCatalogSeeder.loadCatalog();
        assertTrue(items.size() >= 200);
        Set<String> names = new HashSet<>();
        for (var item : items) {
            assertTrue(names.add(item.name()), "重复菜名: " + item.name());
            assertFalse(item.description().isBlank());
            assertFalse(item.image().isBlank());
            assertTrue(item.minutes() >= 5 && item.minutes() <= 120);
            assertFalse(item.ingredients().matches(".*(主料|配菜|适量食材).*"));
            List<String> ingredients = json.readValue(item.ingredients(), new TypeReference<>() { });
            List<String> steps = json.readValue(item.steps(), new TypeReference<>() { });
            assertTrue(ingredients.size() >= 2, item.name());
            assertTrue(ingredients.stream().allMatch(value -> value.matches(".*\\d.*")), item.name());
            assertTrue(steps.size() >= 3, item.name());
        }
        assertEquals(items.size(), names.size());
    }

    @Test
    void upgradesPlaceholderInPlaceWithoutChangingItsId() throws Exception {
        RecipeRepository repository = mock(RecipeRepository.class);
        Recipe existing = new Recipe();
        existing.setId(42L);
        existing.setName(RecipeCatalogSeeder.loadCatalog().get(0).name());
        existing.setIngredients("[\"主料\",\"配菜\"]");
        when(repository.findByName(existing.getName())).thenReturn(Optional.of(existing));
        when(repository.findByName(anyString())).thenReturn(Optional.empty());
        when(repository.findByName(existing.getName())).thenReturn(Optional.of(existing));
        when(repository.findAll()).thenReturn(List.of());

        new RecipeCatalogSeeder(repository).run(new DefaultApplicationArguments(new String[0]));

        assertEquals(42L, existing.getId());
        assertFalse(existing.getIngredients().contains("主料"));
        assertFalse(existing.getSteps().isBlank());
    }
}
