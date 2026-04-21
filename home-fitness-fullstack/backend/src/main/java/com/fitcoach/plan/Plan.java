package com.fitcoach.plan;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * 训练计划
 */
@Entity
@Table(name = "t_plan")
@EntityListeners(AuditingEntityListener.class)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Plan {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String title;

    @Column(length = 256)
    private String description;

    /** NEWBIE | INTERMEDIATE | ADVANCED */
    @Column(length = 16)
    private String level;

    @Column(length = 128)
    private String cover;

    private Integer days;

    /** JSON：训练项列表（每日动作配置） */
    @Column(columnDefinition = "TEXT")
    private String itemsJson;

    private Boolean official = false;
    private Boolean published = true;

    private Long authorId;

    private Integer adoptCount = 0;

    @CreatedDate
    private LocalDateTime createdAt;
}
