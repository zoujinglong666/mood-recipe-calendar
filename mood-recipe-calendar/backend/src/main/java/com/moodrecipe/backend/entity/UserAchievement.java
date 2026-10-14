package com.moodrecipe.backend.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "user_achievements")
public class UserAchievement {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String openid;

    @Column(name = "achievement_code")
    private String achievementCode;

    @Column(name = "unlocked_at")
    private LocalDateTime unlockedAt;
}
