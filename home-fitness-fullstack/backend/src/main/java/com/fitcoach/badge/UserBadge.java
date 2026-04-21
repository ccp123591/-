package com.fitcoach.badge;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

/**
 * 用户已解锁徽章
 */
@Entity
@Table(name = "t_user_badge",
    uniqueConstraints = @UniqueConstraint(columnNames = {"userId", "badgeCode"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserBadge {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false, length = 32)
    private String badgeCode;

    private LocalDateTime unlockedAt = LocalDateTime.now();
}
