package com.fitcoach.social;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "t_post_like",
    uniqueConstraints = @UniqueConstraint(columnNames = {"postId", "userId"}),
    indexes = @Index(name = "idx_postlike_user", columnList = "userId"))
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PostLike {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long postId;

    @Column(nullable = false)
    private Long userId;

    private LocalDateTime createdAt = LocalDateTime.now();
}
