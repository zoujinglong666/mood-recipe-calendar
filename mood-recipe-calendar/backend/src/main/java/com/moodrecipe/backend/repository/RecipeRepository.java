package com.moodrecipe.backend.repository;

import com.moodrecipe.backend.entity.Recipe;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface RecipeRepository extends JpaRepository<Recipe, Long> {

    /** 按心情标签模糊匹配（mood_tags 字段是逗号分隔的字符串） */
    @Query("SELECT r FROM Recipe r WHERE r.moodTags LIKE %:mood%")
    List<Recipe> findByMoodTag(@Param("mood") String mood);

    /** 随机推荐一道匹配心情的菜 */
    @Query(value = "SELECT * FROM recipes WHERE mood_tags LIKE %:mood% ORDER BY RAND() LIMIT 1", nativeQuery = true)
    Recipe findRandomByMood(@Param("mood") String mood);

    /** 锅仔智能体生成且带有真实图片（非本地 /static 占位）的菜谱池 */
    @Query("SELECT r FROM Recipe r WHERE r.source = 'AI' AND r.image IS NOT NULL AND r.image != '' AND r.image NOT LIKE '/static/%'")
    List<Recipe> findAiWithImages();

    Optional<Recipe> findByName(String name);
}
