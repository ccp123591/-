package com.fitcoach.challenge;

import com.fitcoach.exception.BusinessException;
import com.fitcoach.session.Session;
import com.fitcoach.session.SessionRepository;
import com.fitcoach.user.User;
import com.fitcoach.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 挑战赛业务：列表 / 详情 / 报名（幂等）/ 同步进度 / 排行榜。
 * 进度根据 t_session 中 startDate ~ endDate 区间内对应 action 的累计 reps 实时聚合。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChallengeService {

    private final ChallengeRepository challengeRepo;
    private final ChallengeParticipantRepository participantRepo;
    private final SessionRepository sessionRepo;
    private final UserRepository userRepo;

    public List<ChallengeResponse> listActive(Long me) {
        LocalDate today = LocalDate.now();
        return challengeRepo.findByStatusOrderByCreatedAtDesc("ACTIVE").stream()
                .filter(c -> isWithinWindow(c, today))
                .map(c -> toResponse(c, me))
                .toList();
    }

    public ChallengeResponse detail(Long id, Long me) {
        Challenge c = challengeRepo.findById(id)
                .orElseThrow(() -> new BusinessException(404, "挑战不存在"));
        return toResponse(c, me);
    }

    @Transactional
    public ChallengeResponse join(Long userId, Long challengeId) {
        Challenge c = challengeRepo.findById(challengeId)
                .orElseThrow(() -> new BusinessException(404, "挑战不存在"));
        if (!"ACTIVE".equals(c.getStatus())) {
            throw new BusinessException(400, "挑战已结束");
        }
        LocalDate today = LocalDate.now();
        try {
            if (c.getStartDate() != null && today.isBefore(LocalDate.parse(c.getStartDate()))) {
                throw new BusinessException(400, "Challenge has not started");
            }
            if (c.getEndDate() != null && today.isAfter(LocalDate.parse(c.getEndDate()))) {
                throw new BusinessException(400, "Challenge has ended");
            }
        } catch (java.time.format.DateTimeParseException e) {
            throw new BusinessException(500, "Invalid challenge date configuration");
        }
        if (!participantRepo.existsByChallengeIdAndUserId(challengeId, userId)) {
            ChallengeParticipant p = ChallengeParticipant.builder()
                    .challengeId(challengeId)
                    .userId(userId)
                    .joinedAt(LocalDateTime.now())
                    .progressReps(0)
                    .completed(false)
                    .build();
            participantRepo.save(p);
            log.info("[challenge] user={} joined challenge={}", userId, challengeId);
        }
        // 报名后立即同步一次进度（基于已有 session）
        syncProgress(userId, c);
        return toResponse(c, userId);
    }

    /** session 创建后或定时任务都可触发；幂等。 */
    @Transactional
    public void syncProgress(Long userId, Challenge c) {
        ChallengeParticipant p = participantRepo.findByChallengeIdAndUserId(c.getId(), userId).orElse(null);
        if (p == null) return;
        int total = computeReps(userId, c);
        boolean completed = total >= c.getTargetReps();
        p.setProgressReps(total);
        p.setCompleted(completed);
        participantRepo.save(p);
    }

    /** session 创建钩子：对当前用户所有 ACTIVE 挑战检查并刷新匹配 action 的进度。 */
    @Transactional
    public void onSessionCreated(Long userId, String action, Integer reps) {
        if (action == null || reps == null || reps <= 0) return;
        for (Challenge c : challengeRepo.findByStatusOrderByCreatedAtDesc("ACTIVE")) {
            if (isWithinWindow(c, LocalDate.now())
                    && action.equals(c.getAction())
                    && participantRepo.existsByChallengeIdAndUserId(c.getId(), userId)) {
                syncProgress(userId, c);
            }
        }
    }

    public List<ChallengeRankRow> rank(Long challengeId, int limit) {
        challengeRepo.findById(challengeId)
                .orElseThrow(() -> new BusinessException(404, "挑战不存在"));
        int safeLimit = Math.max(1, Math.min(limit, 100));
        var rows = participantRepo.findByChallengeIdOrderByProgressRepsDesc(challengeId,
                PageRequest.of(0, safeLimit));
        Map<Long, User> userById = new HashMap<>();
        userRepo.findAllById(rows.stream().map(p -> p.getUserId()).toList())
                .forEach(u -> userById.put(u.getId(), u));
        List<ChallengeRankRow> out = new ArrayList<>();
        int r = 1;
        for (var p : rows) {
            User u = userById.get(p.getUserId());
            out.add(ChallengeRankRow.builder()
                    .rank(r++)
                    .userId(p.getUserId())
                    .nickname(u == null ? "匿名" : u.getNickname())
                    .avatar(u == null || u.getAvatar() == null ? "" : u.getAvatar())
                    .progressReps(p.getProgressReps())
                    .completed(p.getCompleted())
                    .build());
        }
        return out;
    }

    // ---- helpers ----

    private boolean isWithinWindow(Challenge c, LocalDate day) {
        try {
            if (c.getStartDate() != null && day.isBefore(LocalDate.parse(c.getStartDate()))) return false;
            return c.getEndDate() == null || !day.isAfter(LocalDate.parse(c.getEndDate()));
        } catch (java.time.format.DateTimeParseException e) {
            log.error("[challenge] invalid date config challenge={} start={} end={}",
                    c.getId(), c.getStartDate(), c.getEndDate());
            return false;
        }
    }

    private int computeReps(Long userId, Challenge c) {
        List<Session> all = sessionRepo.findByUserIdOrderBySessionDateDesc(userId);
        int total = 0;
        String start = c.getStartDate();
        String end = c.getEndDate();
        for (Session s : all) {
            if (!c.getAction().equals(s.getAction())) continue;
            String d = s.getSessionDate();
            if (d == null) continue;
            if (start != null && d.compareTo(start) < 0) continue;
            if (end != null && d.compareTo(end) > 0) continue;
            if (s.getReps() != null) total += s.getReps();
        }
        return total;
    }

    private ChallengeResponse toResponse(Challenge c, Long me) {
        ChallengeResponse.ChallengeResponseBuilder b = ChallengeResponse.builder()
                .id(c.getId())
                .title(c.getTitle())
                .description(c.getDescription())
                .action(c.getAction())
                .targetReps(c.getTargetReps())
                .startDate(c.getStartDate())
                .endDate(c.getEndDate())
                .status(c.getStatus())
                .cover(c.getCover())
                .createdAt(c.getCreatedAt())
                .participantCount(participantRepo.countByChallengeId(c.getId()));
        if (me != null) {
            participantRepo.findByChallengeIdAndUserId(c.getId(), me).ifPresentOrElse(
                    p -> b.joined(true).myProgress(p.getProgressReps()).myCompleted(p.getCompleted()),
                    () -> b.joined(false).myProgress(0).myCompleted(false)
            );
        }
        return b.build();
    }
}
