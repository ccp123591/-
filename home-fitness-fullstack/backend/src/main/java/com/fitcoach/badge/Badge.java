package com.fitcoach.badge;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * 成就徽章定义
 */
@Entity
@Table(name = "t_badge")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Badge {
    @Id
    @Column(length = 32)
    private String code;

    @Column(nullable = false, length = 32)
    private String name;

    @Column(length = 128)
    private String description;

    @Column(length = 16)
    private String icon;

    /** JSON: 解锁条件 */
    @Column(columnDefinition = "TEXT")
    private String criteriaJson;

    private Integer sortOrder = 0;
}
