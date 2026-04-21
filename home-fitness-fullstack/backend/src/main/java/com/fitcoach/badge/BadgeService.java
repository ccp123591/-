package com.fitcoach.badge;

import com.fitcoach.exception.BusinessException;
import com.fitcoach.session.Session;
import com.fitcoach.session.SessionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class BadgeService {

    private final BadgeRepository badgeRepo;
    private final UserBadgeRepository userBadgeRepo;
    private final SessionRepository sessionRepo;
    private final ObjectMapper mapper = new ObjectMapper();

    public List<Map<String, Object>> allWithMineFlag(Long userId) {
        Set<String> mine = new HashSet<>();
        if (userId != null) {
            userBadgeRepo.findByUserId(userId).forEach(ub -> mine.add(ub.getBadgeCode()));
        }
        return badgeRepo.findAll().stream()
                .sorted(Comparator.comparingInt(b -> b.getSortOrder() == null ? 99 : b.getSortOrder()))
                .map(b -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("code", b.getCode());
                    m.put("name", b.getName());
                    m.put("description", b.getDescription());
                    m.put("icon", b.getIcon());
                    m.put("unlocked", mine.contains(b.getCode()));
                    return m;
                }).toList();
    }

    public List<Map<String, Object>> mine(Long userId) {
        List<UserBadge> mine = userBadgeRepo.findByUserId(userId);
        Map<String, Badge> defByCode = new HashMap<>();
        badgeRepo.findAll().forEach(b -> defByCode.put(b.getCode(), b));
        return mine.stream().map(ub -> {
            Map<String, Object> m = new HashMap<>();
            m.put("code", ub.getBadgeCode());
            m.put("unlockedAt", ub.getUnlockedAt());
            Badge def = defByCode.get(ub.getBadgeCode());
            if (def != null) {
                m.put("name", def.getName());
                m.put("description", def.getDescription());
                m.put("icon", def.getIcon());
            }
            return m;
        }).toList();
    }

    /** 训练完成后调用：比对条件，解锁新徽章，返回本次新解锁的列表 */
    @Transactional
    public List<Map<String, Object>> check(Long userId) {
        if (userId == null) throw new BusinessException(401, "未登录");

        // 预聚合用户数据
        long sessions = sessionRepo.countByUserId(userId);
        Long totalRepsRaw = sessionRepo.sumRepsByUserId(userId);
        long totalReps = totalRepsRaw == null ? 0 : totalRepsRaw;
        List<Session> all = sessionRepo.findByUserIdOrderBySessionDateDesc(userId);
        int bestScore = all.stream().mapToInt(s -> s.getScore() == null ? 0 : s.getScore()).max().orElse(0);
        int bestRhythm = all.stream().mapToInt(s -> s.getRhythmScore() == null ? 0 : s.getRhythmScore()).max().orElse(0);
        Set<String> distinctDays = new HashSet<>();
        all.forEach(s -> { if (s.getSessionDate() != null) distinctDays.add(s.getSessionDate()); });
        int totalDays = distinctDays.size();
        int streakDays = computeStreak(distinctDays);

        List<Map<String, Object>> newly = new ArrayList<>();
        for (Badge def : badgeRepo.findAll()) {
            if (userBadgeRepo.existsByUserIdAndBadgeCode(userId, def.getCode())) continue;
            if (meets(def, sessions, totalReps, bestScore, bestRhythm, streakDays, totalDays)) {
                UserBadge ub = new UserBadge();
                ub.setUserId(userId);
                ub.setBadgeCode(def.getCode());
                userBadgeRepo.save(ub);
                Map<String, Object> m = new HashMap<>();
                m.put("code", def.getCode());
                m.put("name", def.getName());
                m.put("icon", def.getIcon());
                newly.add(m);
            }
        }
        return newly;
    }

    private boolean meets(Badge def, long sessions, long totalReps,
                          int bestScore, int bestRhythm, int streakDays, int totalDays) {
        try {
            Map<String, Object> c = mapper.readValue(
                    def.getCriteriaJson() == null ? "{}" : def.getCriteriaJson(), Map.class);
            if (c.get("sessions") instanceof Number n && sessions < n.longValue()) return false;
            if (c.get("totalReps") instanceof Number n && totalReps < n.longValue()) return false;
            if (c.get("bestScore") instanceof Number n && bestScore < n.intValue()) return false;
            if (c.get("bestRhythm") instanceof Number n && bestRhythm < n.intValue()) return false;
            if (c.get("streakDays") instanceof Number n && streakDays < n.intValue()) return false;
            if (c.get("totalDays") instanceof Number n && totalDays < n.intValue()) return false;
            return true;
        } catch (Exception e) {
            log.warn("徽章条件解析失败: {} {}", def.getCode(), e.getMessage());
            return false;
        }
    }

    private int computeStreak(Set<String> days) {
        if (days.isEmpty()) return 0;
        int streak = 0;
        LocalDate cursor = LocalDate.now();
        while (days.contains(cursor.toString())) {
            streak++;
            cursor = cursor.minusDays(1);
        }
        return streak;
    }
}
