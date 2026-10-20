package com.moodrecipe.backend.repository;
import com.moodrecipe.backend.entity.UserFeedback; import org.springframework.data.jpa.repository.JpaRepository; import java.time.LocalDateTime; import java.util.List;
import org.springframework.data.domain.Page; import org.springframework.data.domain.Pageable;
public interface UserFeedbackRepository extends JpaRepository<UserFeedback,Long>{ List<UserFeedback> findByOpenidOrderByCreatedAtDesc(String openid); List<UserFeedback> findByCreatedAtBetweenOrderByCreatedAtDesc(LocalDateTime start,LocalDateTime end); Page<UserFeedback> findAllByOrderByCreatedAtDesc(Pageable pageable); Page<UserFeedback> findByStatusOrderByCreatedAtDesc(String status,Pageable pageable); long countByStatus(String status); }
