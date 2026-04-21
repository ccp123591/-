package com.fitcoach.user;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * 用户实体
 */
@Entity
@Table(name = "t_user",
    indexes = {
        @Index(name = "idx_email", columnList = "email"),
        @Index(name = "idx_phone", columnList = "phone")
    })
@EntityListeners(AuditingEntityListener.class)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, length = 64)
    private String email;

    @Column(length = 20)
    private String phone;

    @Column(length = 128)
    private String passwordHash;

    @Column(nullable = false, length = 32)
    private String nickname;

    @Column(length = 256)
    private String avatar;

    /** USER · ADMIN · GUEST */
    @Column(nullable = false, length = 16)
    private String role = "USER";

    /** 登录类型: email / phone / wechat / guest */
    @Column(length = 16)
    private String loginType;

    @Column(length = 128)
    private String openId;

    @Column(length = 128)
    private String deviceId;

    /** ACTIVE · DISABLED */
    @Column(nullable = false, length = 16)
    private String status = "ACTIVE";

    private Integer weeklyGoal = 50;

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;
}
