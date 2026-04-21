package com.fitcoach.user;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface UserFollowRepository extends JpaRepository<UserFollow, Long> {
    List<UserFollow> findByFollowerId(Long followerId);
    List<UserFollow> findByFollowingId(Long followingId);
    boolean existsByFollowerIdAndFollowingId(Long followerId, Long followingId);
    long deleteByFollowerIdAndFollowingId(Long followerId, Long followingId);
    long countByFollowerId(Long followerId);
    long countByFollowingId(Long followingId);
}
