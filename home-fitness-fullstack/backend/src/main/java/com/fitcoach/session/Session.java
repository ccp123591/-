package com.fitcoach.session;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * 训练会话记录
 */
@Entity
@Table(name = "t_session",
    indexes = {
        @Index(name = "idx_user_date", columnList = "userId,sessionDate DESC"),
        @Index(name = "idx_action", columnList = "action")
    })
@EntityListeners(AuditingEntityListener.class)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Session {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false, length = 32)
    private String action;

    @Column(length = 32)
    private String actionLabel;

    @Column(nullable = false)
    private Integer reps;

    private Integer targetReps;

    @Column(nullable = false)
    private Integer duration;

    private Integer score;
    private Integer rhythmScore;
    private Integer stabilityScore;
    private Integer depthScore;
    private Integer symmetryScore;
    private Integer completionScore;

    @Column(length = 32)
    private String sessionDate;

    @Column(length = 512)
    private String notes;

    @CreatedDate
    private LocalDateTime createdAt;
}
