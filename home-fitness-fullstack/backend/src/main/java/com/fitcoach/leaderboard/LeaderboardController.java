package com.fitcoach.leaderboard;

import com.fitcoach.common.ApiResult;
import com.fitcoach.security.SecurityUtil;
import com.fitcoach.session.SessionRepository;
import com.fitcoach.user.User;
import com.fitcoach.user.UserFollowRepository;
import com.fitcoach.user.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Tag(name = "07. 排行榜", description = "周榜 / 月榜 / 好友榜")
@RestController
@RequestMapping("/api/leaderboard")
@RequiredArgsConstructor
public class LeaderboardController {

    private final SessionRepository sessionRepo;
    private final UserRepository userRepo;
    private final UserFollowRepository followRepo;

    @Operation(summary = "本周排行榜")
    @GetMapping("/weekly")
    public ApiResult<List<Map<String, Object>>> weekly() {
        return ApiResult.ok(build(LocalDate.now().minusDays(6).toString(), null));
    }

    @Operation(summary = "本月排行榜")
    @GetMapping("/monthly")
    public ApiResult<List<Map<String, Object>>> monthly() {
        LocalDate first = LocalDate.now().withDayOfMonth(1);
        return ApiResult.ok(build(first.toString(), null));
    }

    @Operation(summary = "好友排行榜（本周）")
    @GetMapping("/friends")
    public ApiResult<List<Map<String, Object>>> friends() {
        Long me = SecurityUtil.currentUserIdOrNull();
        if (me == null) return ApiResult.ok(List.of());
        Set<Long> scope = new HashSet<>();
        scope.add(me);
        followRepo.findByFollowerId(me).forEach(f -> scope.add(f.getFollowingId()));
        return ApiResult.ok(build(LocalDate.now().minusDays(6).toString(), scope));
    }

    private List<Map<String, Object>> build(String startDate, Set<Long> scopeUserIds) {
        var rows = sessionRepo.aggregateSince(startDate, PageRequest.of(0, 100));
        List<Map<String, Object>> all = new ArrayList<>();
        int rank = 1;
        for (var row : rows) {
            if (scopeUserIds != null && !scopeUserIds.contains(row.getUserId())) continue;
            User u = userRepo.findById(row.getUserId()).orElse(null);
            Map<String, Object> m = new HashMap<>();
            m.put("rank", rank++);
            m.put("userId", row.getUserId());
            m.put("name", u == null ? "匿名用户" : u.getNickname());
            m.put("avatar", u == null || u.getAvatar() == null ? "" : u.getAvatar());
            m.put("reps", row.getTotalReps());
            m.put("score", row.getAvgScore() == null ? 0 : Math.round(row.getAvgScore()));
            all.add(m);
            if (all.size() >= 20) break;
        }
        return all;
    }
}
