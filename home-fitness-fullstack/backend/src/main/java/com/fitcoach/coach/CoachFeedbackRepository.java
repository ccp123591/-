package com.fitcoach.coach;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CoachFeedbackRepository extends JpaRepository<CoachFeedback, Long> {
    Optional<CoachFeedback> findBySessionId(Long sessionId);
    List<CoachFeedback> findByUserIdOrderByCreatedAtDesc(Long userId);
}
