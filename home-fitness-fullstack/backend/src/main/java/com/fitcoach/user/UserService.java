package com.fitcoach.user;

import com.fitcoach.exception.BusinessException;
import com.fitcoach.session.Session;
import com.fitcoach.session.SessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepo;
    private final SessionRepository sessionRepo;
    private final UserFollowRepository followRepo;

    @Value("${upload.dir:./uploads}")
    private String uploadDir;

    public Map<String, Object> profile(Long userId) {
        User u = userRepo.findById(userId)
                .orElseThrow(() -> new BusinessException(404, "用户不存在"));
        Map<String, Object> m = new HashMap<>();
        m.put("id", u.getId());
        m.put("nickname", u.getNickname());
        m.put("email", u.getEmail() == null ? "" : u.getEmail());
        m.put("phone", u.getPhone() == null ? "" : u.getPhone());
        m.put("avatar", u.getAvatar() == null ? "" : u.getAvatar());
        m.put("role", u.getRole());
        m.put("weeklyGoal", u.getWeeklyGoal());
        m.put("createdAt", u.getCreatedAt());
        return m;
    }

    @Transactional
    public void updateProfile(Long userId, Map<String, Object> body) {
        User u = userRepo.findById(userId)
                .orElseThrow(() -> new BusinessException(404, "用户不存在"));
        if (body.get("nickname") instanceof String s && !s.isBlank()) u.setNickname(s);
        if (body.get("avatar")   instanceof String s)  u.setAvatar(s);
        if (body.get("weeklyGoal") instanceof Number n) u.setWeeklyGoal(n.intValue());
        userRepo.save(u);
    }

    @Transactional
    public String saveAvatar(Long userId, MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) throw new BusinessException(400, "文件为空");
        Path dir = Paths.get(uploadDir, "avatars");
        Files.createDirectories(dir);
        String ext = "";
        String original = file.getOriginalFilename();
        if (original != null && original.contains(".")) {
            ext = original.substring(original.lastIndexOf('.'));
        }
        String filename = "u" + userId + "-" + System.currentTimeMillis() + ext;
        Path target = dir.resolve(filename);
        file.transferTo(target.toFile());
        String url = "/uploads/avatars/" + filename;
        User u = userRepo.findById(userId)
                .orElseThrow(() -> new BusinessException(404, "用户不存在"));
        u.setAvatar(url);
        userRepo.save(u);
        return url;
    }

    /** 个人统计：总次数、总 reps、最高分、连续天数 */
    public Map<String, Object> stats(Long userId) {
        long totalSessions = sessionRepo.countByUserId(userId);
        Long totalReps = sessionRepo.sumRepsByUserId(userId);
        List<Session> all = sessionRepo.findByUserIdOrderBySessionDateDesc(userId);
        int bestScore = all.stream().mapToInt(s -> s.getScore() == null ? 0 : s.getScore()).max().orElse(0);
        int streakDays = computeStreak(all);
        Map<String, Object> r = new HashMap<>();
        r.put("totalSessions", totalSessions);
        r.put("totalReps", totalReps == null ? 0 : totalReps);
        r.put("bestScore", bestScore);
        r.put("streakDays", streakDays);
        return r;
    }

    /** 月历签到：yyyy-MM → [{date:"yyyy-MM-dd", count:N, reps:N}] */
    public List<Map<String, Object>> calendar(Long userId, String yearMonth) {
        if (yearMonth == null || yearMonth.isBlank()) {
            yearMonth = YearMonth.now().toString();
        }
        String prefix = yearMonth; // "yyyy-MM"
        List<Session> sessions = sessionRepo.findByUserIdSince(userId, prefix + "-01");
        Map<String, int[]> agg = new TreeMap<>();
        for (Session s : sessions) {
            String d = s.getSessionDate();
            if (d == null || !d.startsWith(prefix)) continue;
            int[] cur = agg.computeIfAbsent(d, k -> new int[2]);
            cur[0] += 1;
            cur[1] += (s.getReps() == null ? 0 : s.getReps());
        }
        return agg.entrySet().stream().map(e -> {
            Map<String, Object> m = new HashMap<>();
            m.put("date", e.getKey());
            m.put("count", e.getValue()[0]);
            m.put("reps", e.getValue()[1]);
            return m;
        }).collect(Collectors.toList());
    }

    /* ---------- follow ---------- */

    @Transactional
    public void follow(Long me, Long targetId) {
        if (Objects.equals(me, targetId)) throw new BusinessException(400, "不能关注自己");
        if (!userRepo.existsById(targetId)) throw new BusinessException(404, "目标用户不存在");
        if (followRepo.existsByFollowerIdAndFollowingId(me, targetId)) return;
        UserFollow f = new UserFollow();
        f.setFollowerId(me);
        f.setFollowingId(targetId);
        followRepo.save(f);
    }

    @Transactional
    public void unfollow(Long me, Long targetId) {
        followRepo.deleteByFollowerIdAndFollowingId(me, targetId);
    }

    public List<Map<String, Object>> followers(Long userId) {
        return followRepo.findByFollowingId(userId).stream()
                .map(f -> userBrief(f.getFollowerId()))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    public List<Map<String, Object>> followings(Long userId) {
        return followRepo.findByFollowerId(userId).stream()
                .map(f -> userBrief(f.getFollowingId()))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private Map<String, Object> userBrief(Long id) {
        return userRepo.findById(id).map(u -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", u.getId());
            m.put("nickname", u.getNickname());
            m.put("avatar", u.getAvatar() == null ? "" : u.getAvatar());
            return m;
        }).orElse(null);
    }

    /* ---------- private ---------- */

    private int computeStreak(List<Session> sessions) {
        if (sessions.isEmpty()) return 0;
        Set<String> days = sessions.stream()
                .map(Session::getSessionDate)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        int streak = 0;
        LocalDate cursor = LocalDate.now();
        while (days.contains(cursor.toString())) {
            streak++;
            cursor = cursor.minusDays(1);
        }
        return streak;
    }
}
