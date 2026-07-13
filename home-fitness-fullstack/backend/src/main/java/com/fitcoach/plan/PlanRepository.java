package com.fitcoach.plan;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface PlanRepository extends JpaRepository<Plan, Long> {
    List<Plan> findByOfficialTrueAndPublishedTrueOrderByAdoptCountDesc();
    List<Plan> findByAuthorId(Long authorId);
    Page<Plan> findByPublishedTrue(Pageable pageable);
    Page<Plan> findByOfficialFalseAndPublishedTrue(Pageable pageable);

    @Modifying
    @Query(value = "UPDATE t_plan SET adopt_count = COALESCE(adopt_count, 0) + 1 WHERE id = :id", nativeQuery = true)
    int incrementAdoptCount(@Param("id") Long id);
}
