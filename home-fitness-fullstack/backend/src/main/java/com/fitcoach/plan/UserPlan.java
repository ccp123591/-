package com.fitcoach.plan;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "t_user_plan",
    uniqueConstraints = @UniqueConstraint(columnNames = {"userId", "planId"}),
    indexes = @Index(name = "idx_user_plan_user", columnList = "userId"))
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserPlan {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Long planId;

    @Column(nullable = false)
    private Integer progressDay = 0;

    @Column(length = 16)
    private String status = "ACTIVE";  // ACTIVE / COMPLETED / ABANDONED

    private LocalDateTime adoptedAt = LocalDateTime.now();
    private LocalDateTime updatedAt = LocalDateTime.now();
}
