package com.moodrecipe.backend.repository;
import com.moodrecipe.backend.entity.OperationalEvent;
import org.springframework.data.jpa.repository.JpaRepository;
public interface OperationalEventRepository extends JpaRepository<OperationalEvent, Long> { }
