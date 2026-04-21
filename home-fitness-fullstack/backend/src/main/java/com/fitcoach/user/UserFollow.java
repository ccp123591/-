package com.fitcoach.user;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "t_user_follow",
    uniqueConstraints = @UniqueConstraint(columnNames = {"followerId", "followingId"}),
    indexes = {
        @Index(name = "idx_follower", columnList = "followerId"),
        @Index(name = "idx_following", columnList = "followingId")
    })
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserFollow {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long followerId;

    @Column(nullable = false)
    private Long followingId;

    private LocalDateTime createdAt = LocalDateTime.now();
}
