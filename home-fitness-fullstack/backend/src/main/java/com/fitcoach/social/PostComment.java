package com.fitcoach.social;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "t_post_comment",
    indexes = @Index(name = "idx_post_created", columnList = "postId,createdAt"))
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PostComment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long postId;

    @Column(nullable = false)
    private Long userId;

    @Column(length = 500, nullable = false)
    private String content;

    private LocalDateTime createdAt = LocalDateTime.now();
}
