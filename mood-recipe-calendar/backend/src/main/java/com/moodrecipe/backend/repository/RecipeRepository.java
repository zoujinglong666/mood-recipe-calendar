package com.moodrecipe.backend.repository;

import com.moodrecipe.backend.entity.Recipe;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface RecipeRepository extends JpaRepository<Recipe, Long>, JpaSpecificationExecutor<Recipe> {

    /** 按心情标签模糊匹配（mood_tags 字段是逗号分隔的字符串） */
    @Query("SELECT r FROM Recipe r WHERE r.moodTags LIKE %:mood%")
    List<Recipe> findByMoodTag(@Param("mood") String mood);

    /** 定时精准分类取批：从未分类的优先（NULL 判定式排序），其余按最久未复核在前，稳定按 id。 */
    @Query(value = "SELECT * FROM recipes ORDER BY (mood_classified_at IS NULL) DESC, mood_classified_at ASC, id ASC LIMIT :limit", nativeQuery = true)
    List<Recipe> findClassificationBatch(@Param("limit") int limit);

    /** 随机推荐一道匹配心情的菜 */
    @Query(value = "SELECT * FROM recipes WHERE mood_tags LIKE %:mood% ORDER BY RAND() LIMIT 1", nativeQuery = true)
    Recipe findRandomByMood(@Param("mood") String mood);

    /** 锅仔智能体生成的菜谱池（推荐/抽奖/三餐 AI 池共用）。
     *  菜名是核心资产：无图的菜也进池（前端有兜底插画）；
     *  /static 占位与 doubaocdn 死链一律不返回（死链由 DeadRecipeImageCleaner 启动时置空）。 */
    @Query("SELECT r FROM Recipe r WHERE r.source = 'AI' "
            + "AND (r.image IS NULL OR (r.image NOT LIKE '/static/%' AND r.image NOT LIKE '%doubaocdn.com%'))")
    List<Recipe> findAiWithImages();

    /** 把过期的 doubaocdn 短链图置空：短链时效已过（403），置空后菜谱凭菜名回池。 */
    @Modifying
    @Query("UPDATE Recipe r SET r.image = NULL WHERE r.image LIKE '%doubaocdn.com%'")
    int nullOutDeadImages();

    /** 早餐组合菜谱池数量（moodTags 打了「早餐」标签的菜谱，三餐生成的早餐槽从这里选）。 */
    long countBySourceAndMoodTagsContaining(String source, String tag);

    /** 早餐池中无真实图（无图 / static 占位 / doubaocdn 死链）的组合，供启动时补拍专属封面。 */
    @Query("SELECT r FROM Recipe r WHERE r.source = 'AI' AND r.moodTags LIKE %:tag% "
            + "AND (r.image IS NULL OR r.image = '' OR r.image LIKE '/static/%' OR r.image LIKE '%doubaocdn.com%')")
    List<Recipe> findAiWithMissingImage(@Param("tag") String tag);

    Optional<Recipe> findByName(String name);

    /** 管理批量配图：菜名包含匹配（取第一条命中）。 */
    List<Recipe> findByNameContainingOrderByIdAsc(String keyword);

    /** 仅取 mood_tags 列（不含大 TEXT 字段），供菜谱库标签云聚合（分页接口拿不到全量）。 */
    @Query("SELECT r.moodTags FROM Recipe r WHERE r.moodTags IS NOT NULL")
    List<String> findAllMoodTags();

    /** 同名重复的菜名清单（每日去重分析用），只取轻量列避免全表 TEXT 物化。 */
    @Query(value = "SELECT name FROM recipes WHERE name IS NOT NULL AND name <> '' "
            + "GROUP BY name HAVING COUNT(*) > 1", nativeQuery = true)
    List<String> findDuplicatedNames();

    /** 轻量 id+菜名清单（采集任务比对未收录、均衡性画像用）。 */
    @Query("SELECT r.id, r.name FROM Recipe r WHERE r.name IS NOT NULL")
    List<Object[]> findAllIdAndName();

    /** 同名菜的所有版本（去重分析按行给出保留/淘汰建议）。 */
    List<Recipe> findByNameOrderByIdAsc(String name);
}
