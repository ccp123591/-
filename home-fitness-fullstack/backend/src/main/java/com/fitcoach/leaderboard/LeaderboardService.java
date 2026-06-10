package com.fitcoach.leaderboard;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitcoach.session.SessionRepository;
import com.fitcoach.user.User;
import com.fitcoach.user.UserFollowRepository;
import com.fitcoach.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.util.*;

/**
 * 排行榜服务 — 周 / 月 / 好友。基于 Redis 60s 缓存，DB 命中后再写缓存。
 * Redis 不可用时透传到 DB（fail-open）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LeaderboardService {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Duration TTL = Duration.ofSeconds(60);
    private static final TypeReference<List<Map<String, Object>>> TYPE =
            new TypeReference<>() {};

    private final SessionRepository sessionRepo;
    private final UserRepository userRepo;
    private final UserFollowRepository followRepo;
    private final StringRedisTemplate redis;

    public List<Map<String, Object>> weekly() {
        return cached("cache:lb:weekly",
                () -> build(LocalDate.now().minusDays(6).toString(), null));
    }

    public List<Map<String, Object>> monthly() {
        return cached("cache:lb:monthly",
                () -> build(LocalDate.now().withDayOfMonth(1).toString(), null));
    }

    public List<Map<String, Object>> friends(Long me) {
        if (me == null) return List.of();
        return cached("cache:lb:friends:" + me, () -> {
            Set<Long> scope = new HashSet<>();
            scope.add(me);
            followRepo.findByFollowerId(me).forEach(f -> scope.add(f.getFollowingId()));
            // 直接按好友集合聚合 — 不再受全局 Top100 截断影响
            return buildRows(sessionRepo.aggregateSinceForUsers(
                    LocalDate.now().minusDays(6).toString(), scope, PageRequest.of(0, 20)));
        });
    }

    /** 管理员或会话写入时调用清缓存。 */
    public void evictAll() {
        try {
            redis.delete(List.of("cache:lb:weekly", "cache:lb:monthly"));
            Set<String> keys = redis.keys("cache:lb:friends:*");
            if (keys != null && !keys.isEmpty()) redis.delete(keys);
        } catch (Exception ignored) {
        }
    }

    // ---- helpers ----

    private List<Map<String, Object>> cached(String key, java.util.function.Supplier<List<Map<String, Object>>> loader) {
        try {
            String json = redis.opsForValue().get(key);
            if (json != null) {
                return MAPPER.readValue(json, TYPE);
            }
        } catch (Exception e) {
            log.debug("[leaderboard] cache read 失败，回源 DB: {}", e.getMessage());
        }
        List<Map<String, Object>> data = loader.get();
        try {
            redis.opsForValue().set(key, MAPPER.writeValueAsString(data), TTL);
        } catch (Exception e) {
            log.debug("[leaderboard] cache write 失败: {}", e.getMessage());
        }
        return data;
    }

    private List<Map<String, Object>> build(String startDate, Set<Long> scopeUserIds) {
        var rows = sessionRepo.aggregateSince(startDate, PageRequest.of(0, 100));
        var top = rows.stream()
                .filter(r -> scopeUserIds == null || scopeUserIds.contains(r.getUserId()))
                .limit(20)
                .toList();
        return buildRows(top);
    }

    private List<Map<String, Object>> buildRows(List<SessionRepository.SessionAggRow> top) {
        Map<Long, User> users = new HashMap<>();
        userRepo.findAllById(top.stream().map(r -> r.getUserId()).toList())
                .forEach(u -> users.put(u.getId(), u));
        List<Map<String, Object>> all = new ArrayList<>();
        int rank = 1;
        for (var row : top) {
            User u = users.get(row.getUserId());
            Map<String, Object> m = new HashMap<>();
            m.put("rank", rank++);
            m.put("userId", row.getUserId());
            m.put("name", u == null ? "匿名用户" : u.getNickname());
            m.put("avatar", u == null || u.getAvatar() == null ? "" : u.getAvatar());
            m.put("reps", row.getTotalReps());
            m.put("score", row.getAvgScore() == null ? 0 : Math.round(row.getAvgScore()));
            all.add(m);
        }
        return all;
    }
}
