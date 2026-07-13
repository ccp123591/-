package com.fitcoach.social;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PostRepository extends JpaRepository<Post, Long> {
    Page<Post> findByVisibilityOrderByCreatedAtDesc(String visibility, Pageable pageable);
    Page<Post> findByUserId(Long userId, Pageable pageable);

    @Modifying
    @Query(value = "UPDATE t_post SET likes = COALESCE(likes, 0) + 1 WHERE id = :id", nativeQuery = true)
    int incrementLikes(@Param("id") Long id);

    @Modifying
    @Query(value = "UPDATE t_post SET likes = GREATEST(COALESCE(likes, 0) - 1, 0) WHERE id = :id", nativeQuery = true)
    int decrementLikes(@Param("id") Long id);

    @Modifying
    @Query(value = "UPDATE t_post SET comments_count = COALESCE(comments_count, 0) + 1 WHERE id = :id", nativeQuery = true)
    int incrementComments(@Param("id") Long id);
}
