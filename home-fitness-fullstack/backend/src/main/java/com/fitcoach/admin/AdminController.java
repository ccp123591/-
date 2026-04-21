package com.fitcoach.admin;

import com.fitcoach.common.ApiResult;
import com.fitcoach.common.PageResult;
import com.fitcoach.session.Session;
import com.fitcoach.session.SessionRepository;
import com.fitcoach.user.User;
import com.fitcoach.user.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Tag(name = "10. 管理后台", description = "仅管理员可访问")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final UserRepository userRepo;
    private final SessionRepository sessionRepo;

    @Operation(summary = "Dashboard 概览")
    @GetMapping("/dashboard")
    public ApiResult<Map<String, Object>> dashboard() {
        long users = userRepo.count();
        long sessions = sessionRepo.count();
        String today = LocalDate.now().toString();
        long todaySessions = sessionRepo.countBySessionDateGreaterThanEqual(today);
        List<Integer> pv7d = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            String d = LocalDate.now().minusDays(i).toString();
            long c = sessionRepo.countBySessionDateGreaterThanEqual(d)
                   - sessionRepo.countBySessionDateGreaterThanEqual(LocalDate.now().minusDays(i - 1).toString());
            pv7d.add((int) Math.max(0, c));
        }
        Map<String, Object> r = new HashMap<>();
        r.put("users", users);
        r.put("sessions", sessions);
        r.put("todaySessions", todaySessions);
        r.put("dau", sessionRepo.countBySessionDateGreaterThanEqual(today));
        r.put("pv7d", pv7d);
        return ApiResult.ok(r);
    }

    @Operation(summary = "用户列表")
    @GetMapping("/users")
    public ApiResult<PageResult<Map<String, Object>>> users(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword) {
        Page<User> p = userRepo.findAll(PageRequest.of(Math.max(0, page - 1), size,
                Sort.by(Sort.Direction.DESC, "createdAt")));
        List<Map<String, Object>> items = p.getContent().stream()
                .filter(u -> keyword == null || keyword.isBlank()
                        || (u.getEmail() != null && u.getEmail().contains(keyword))
                        || (u.getNickname() != null && u.getNickname().contains(keyword)))
                .map(this::userMap).collect(Collectors.toList());
        return ApiResult.ok(PageResult.of(items, p.getTotalElements(), page, size));
    }

    @Operation(summary = "封禁用户")
    @Transactional
    @PostMapping("/users/{id}/ban")
    public ApiResult<Void> ban(@PathVariable Long id) {
        userRepo.findById(id).ifPresent(u -> { u.setStatus("DISABLED"); userRepo.save(u); });
        return ApiResult.ok(null, "已封禁");
    }

    @Operation(summary = "解封用户")
    @Transactional
    @PostMapping("/users/{id}/unban")
    public ApiResult<Void> unban(@PathVariable Long id) {
        userRepo.findById(id).ifPresent(u -> { u.setStatus("ACTIVE"); userRepo.save(u); });
        return ApiResult.ok(null, "已解封");
    }

    @Operation(summary = "训练记录管理")
    @GetMapping("/sessions")
    public ApiResult<PageResult<Session>> sessions(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<Session> p = sessionRepo.findAll(PageRequest.of(Math.max(0, page - 1), size,
                Sort.by(Sort.Direction.DESC, "sessionDate").and(Sort.by(Sort.Direction.DESC, "id"))));
        return ApiResult.ok(PageResult.of(p.getContent(), p.getTotalElements(), page, size));
    }

    @Operation(summary = "数据分析")
    @GetMapping("/analytics")
    public ApiResult<Map<String, Object>> analytics(@RequestParam(required = false) String metric) {
        // 动作分布
        Map<String, Long> actionDist = new LinkedHashMap<>();
        sessionRepo.countByAction().forEach(r -> actionDist.put(r.getAction(), r.getCnt()));

        // 近 7 日 / 30 日活跃
        String d7 = LocalDate.now().minusDays(7).toString();
        String d30 = LocalDate.now().minusDays(30).toString();
        long active7 = sessionRepo.countBySessionDateGreaterThanEqual(d7);
        long active30 = sessionRepo.countBySessionDateGreaterThanEqual(d30);
        long totalUsers = Math.max(1, userRepo.count());

        // 平均分
        List<Session> recent = sessionRepo.findAll(PageRequest.of(0, 500,
                Sort.by(Sort.Direction.DESC, "id"))).getContent();
        double avgScore = recent.stream().mapToInt(s -> s.getScore() == null ? 0 : s.getScore())
                .average().orElse(0);

        Map<String, Object> r = new HashMap<>();
        r.put("retention7d",  round2((double) active7 / totalUsers));
        r.put("retention30d", round2((double) active30 / totalUsers));
        r.put("avgScore", Math.round(avgScore));
        r.put("actionDistribution", actionDist);
        return ApiResult.ok(r);
    }

    private Map<String, Object> userMap(User u) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", u.getId());
        m.put("email", u.getEmail());
        m.put("nickname", u.getNickname());
        m.put("role", u.getRole());
        m.put("status", u.getStatus());
        m.put("createdAt", u.getCreatedAt());
        return m;
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
