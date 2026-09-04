package com.moodrecipe.backend.entity;
import jakarta.persistence.*; import lombok.Data; import java.time.LocalDateTime;
@Data @Entity @Table(name="user_feedback") public class UserFeedback {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(nullable=false,length=64) private String openid;
 @Column(nullable=false,length=32) private String category;
 @Column(nullable=false,length=1000) private String content;
 @Column(length=100) private String contact;
 @Column(nullable=false,length=16) private String status="OPEN";
 @Column(name="created_at") private LocalDateTime createdAt;
 @PrePersist void created(){createdAt=LocalDateTime.now();}
}
