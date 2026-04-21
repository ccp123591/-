package com.fitcoach.coach;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * AI 教练反馈记录
 */
@Entity
@Table(name = "t_coach_feedback")
@EntityListeners(AuditingEntityListener.class)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CoachFeedback {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    private Long sessionId;

    @Column(length = 500)
    private String review;

    @Column(length = 500)
    private String suggestion;

    @Column(length = 300)
    private String encouragement;

    @Column(length = 200)
    private String nextGoal;

    @Column(length = 32)
    private String provider;   // mock / claude / openai

    private Integer tokensUsed = 0;

    @CreatedDate
    private LocalDateTime createdAt;
}
