package com.moodrecipe.backend.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moodrecipe.backend.entity.Recipe;
import com.moodrecipe.backend.entity.ThemeAlbum;
import com.moodrecipe.backend.entity.UserEntitlement;
import com.moodrecipe.backend.entity.VirtualProduct;
import com.moodrecipe.backend.repository.RecipeRepository;
import com.moodrecipe.backend.repository.ThemeAlbumRepository;
import com.moodrecipe.backend.repository.VirtualProductRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ThemeAlbumService {
    private final ThemeAlbumRepository albums;
    private final RecipeRepository recipes;
    private final VirtualProductRepository products;
    private final VirtualCommerceService commerce;
    private final ObjectMapper objectMapper;

    public ThemeAlbumService(ThemeAlbumRepository albums, RecipeRepository recipes,
                             VirtualProductRepository products, VirtualCommerceService commerce,
                             ObjectMapper objectMapper) {
        this.albums = albums;
        this.recipes = recipes;
        this.products = products;
        this.commerce = commerce;
        this.objectMapper = objectMapper;
    }

    /** 专辑列表（含已购态与价格）。 */
    public List<AlbumView> list(String openid) {
        Set<String> owned = ownedCodes(openid);
        return albums.findByActiveTrueOrderBySortOrderAsc().stream()
                .map(a -> new AlbumView(a.getSku(), a.getTitle(), a.getSubtitle(), a.getCover(),
                        a.getDescription(), parseIds(a.getRecipeIds()).size(), owned.contains(a.getSku()),
                        priceOf(a.getSku())))
                .toList();
    }

    /** 专辑详情：未购买只返回前 previewCount 道，且食材/做法置空。 */
    public AlbumDetail detail(String openid, String sku) {
        ThemeAlbum album = albums.findBySkuAndActiveTrue(sku).orElse(null);
        if (album == null) return null;
        boolean owned = ownedCodes(openid).contains(album.getSku());
        List<Long> ids = parseIds(album.getRecipeIds());
        Map<Long, Recipe> map = recipes.findAllById(ids).stream()
                .collect(Collectors.toMap(Recipe::getId, Function.identity(), (a, b) -> a));
        List<DishView> all = ids.stream()
                .map(map::get)
                .filter(Objects::nonNull)
                .map(this::view)
                .toList();
        int visible = owned ? all.size() : Math.max(0, Math.min(album.getPreviewCount(), all.size()));
        List<DishView> dishes = owned ? all : all.subList(0, visible).stream()
                .map(d -> new DishView(d.id(), d.name(), d.image(), d.cookingTime(), d.difficulty(),
                        d.description(), null, null))
                .toList();
        return new AlbumDetail(album.getSku(), album.getTitle(), album.getSubtitle(), album.getCover(),
                album.getDescription(), owned, visible, all.size(), dishes, priceOf(album.getSku()));
    }

    private DishView view(Recipe r) {
        return new DishView(r.getId(), r.getName(), r.getImage(), r.getCookingTime(),
                r.getDifficulty(), r.getDescription(), r.getIngredients(), r.getSteps());
    }

    private Set<String> ownedCodes(String openid) {
        return commerce.listActiveEntitlements(openid).stream()
                .map(UserEntitlement::getCode)
                .collect(Collectors.toSet());
    }

    private Integer priceOf(String sku) {
        return products.findById(sku).map(VirtualProduct::getPriceFen).orElse(null);
    }

    private List<Long> parseIds(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<List<Long>>() {});
        }
        catch (Exception e) {
            // recipe_ids 被运营配错时静默返回空会让前端看到空专辑，必须留痕
            org.slf4j.LoggerFactory.getLogger(ThemeAlbumService.class)
                    .warn("[theme-album] recipe_ids JSON 解析失败，按空处理: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    public record AlbumView(String sku, String title, String subtitle, String cover, String description,
                            int dishCount, boolean owned, Integer priceFen) {}

    public record AlbumDetail(String sku, String title, String subtitle, String cover, String description,
                              boolean owned, int visibleCount, int totalCount, List<DishView> dishes, Integer priceFen) {}

    public record DishView(Long id, String name, String image, Integer cookingTime, String difficulty,
                           String description, String ingredients, String steps) {}
}
