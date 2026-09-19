package com.moodrecipe.backend.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class AccountDeletionService {
    private static final List<String> PRIVATE_TABLES = List.of(
            "agent_memory_facts", "plan_dish_outcomes", "recommendation_exposures",
            "recipe_interactions", "user_feedback", "operational_events", "checkins",
            "monthly_albums", "weekly_meal_plans", "user_food_preferences", "user_records",
            "user_entitlements");

    private final JdbcTemplate jdbc;
    private final CosImageStorageService images;

    public AccountDeletionService(JdbcTemplate jdbc, CosImageStorageService images) {
        this.jdbc = jdbc;
        this.images = images;
    }

    @Transactional
    public void delete(String openid) {
        List<String> urls = new ArrayList<>();
        urls.addAll(jdbc.query("select avatar_url from users where openid = ? and avatar_url is not null",
                (rs, row) -> rs.getString(1), openid));
        urls.addAll(jdbc.query("select image_url from user_records where openid = ? and image_url is not null",
                (rs, row) -> rs.getString(1), openid));
        for (String url : urls) {
            jdbc.update("insert into pending_asset_deletions(object_url,status,attempts,created_at,updated_at) values (?, 'PENDING', 0, ?, ?)",
                    url, LocalDateTime.now(), LocalDateTime.now());
        }

        String anonymous = "deleted_" + sha256(openid).substring(0, 32);
        jdbc.update("update virtual_orders set openid = ? where openid = ?", anonymous, openid);
        jdbc.update("update shop_orders set openid = ? where openid = ?", anonymous, openid);
        for (String table : PRIVATE_TABLES) jdbc.update("delete from " + table + " where openid = ?", openid);
        if (jdbc.update("delete from users where openid = ?", openid) != 1) {
            throw new IllegalArgumentException("用户不存在或已经注销");
        }
    }

    @Scheduled(fixedDelayString = "${privacy.asset-cleanup-delay-ms:300000}")
    public void retryPendingAssets() {
        jdbc.query("select id, object_url from pending_asset_deletions where status = 'PENDING' and attempts < 10 order by id limit 50",
                rs -> {
                    long id = rs.getLong("id");
                    boolean deleted = images.deleteByUrl(rs.getString("object_url"));
                    jdbc.update("update pending_asset_deletions set status = ?, attempts = attempts + 1, updated_at = ? where id = ?",
                            deleted ? "DELETED" : "PENDING", LocalDateTime.now(), id);
                });
    }

    private static String sha256(String value) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("账号匿名化失败", exception);
        }
    }
}
