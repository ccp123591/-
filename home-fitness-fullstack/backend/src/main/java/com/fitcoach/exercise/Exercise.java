package com.fitcoach.exercise;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

/**
 * 动作库
 */
@Entity
@Table(name = "t_exercise")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Exercise {
    @Id
    @Column(length = 32)
    private String code;

    @Column(nullable = false, length = 32)
    private String name;

    @Column(length = 256)
    private String description;

    @Column(length = 32)
    private String kind;  // rep | timed

    @Column(length = 256)
    private String icon;

    @Column(length = 512)
    private String videoUrl;

    /** JSON: landmarks 定义 */
    @Column(columnDefinition = "TEXT")
    private String landmarksJson;

    private Integer defaultThresholdDown;
    private Integer defaultThresholdUp;

    private Boolean enabled = true;

    private Integer sortOrder = 0;
}
