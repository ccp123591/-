package com.fitcoach.social;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PostLikeRepository extends JpaRepository<PostLike, Long> {
    boolean existsByPostIdAndUserId(Long postId, Long userId);
    long deleteByPostIdAndUserId(Long postId, Long userId);
}
