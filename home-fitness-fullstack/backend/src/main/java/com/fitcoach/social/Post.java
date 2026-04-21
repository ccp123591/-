package com.fitcoach.social;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * 训练动态（Post / Feed）
 */
@Entity
@Table(name = "t_post",
    indexes = @Index(name = "idx_user_created", columnList = "userId,createdAt DESC"))
@EntityListeners(AuditingEntityListener.class)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Post {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    private Long sessionId;

    @Column(length = 1000)
    private String content;

    private Integer likes = 0;
    private Integer commentsCount = 0;

    @Column(length = 16)
    private String visibility = "PUBLIC";  // PUBLIC / FRIENDS

    @CreatedDate
    private LocalDateTime createdAt;
}
