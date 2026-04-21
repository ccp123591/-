package com.fitcoach.plan;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserPlanRepository extends JpaRepository<UserPlan, Long> {
    List<UserPlan> findByUserIdAndStatus(Long userId, String status);
    Optional<UserPlan> findByUserIdAndPlanId(Long userId, Long planId);
}
