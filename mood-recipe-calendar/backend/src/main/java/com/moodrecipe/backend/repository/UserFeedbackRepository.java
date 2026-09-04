package com.moodrecipe.backend.repository;
import com.moodrecipe.backend.entity.UserFeedback; import org.springframework.data.jpa.repository.JpaRepository; import java.util.List;
public interface UserFeedbackRepository extends JpaRepository<UserFeedback,Long>{ List<UserFeedback> findByOpenidOrderByCreatedAtDesc(String openid); }
