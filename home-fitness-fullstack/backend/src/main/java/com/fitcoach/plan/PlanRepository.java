package com.fitcoach.plan;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface PlanRepository extends JpaRepository<Plan, Long> {
    List<Plan> findByOfficialTrueAndPublishedTrueOrderByAdoptCountDesc();
    List<Plan> findByAuthorId(Long authorId);
}
