package com.fitcoach.session;

import com.fitcoach.challenge.ChallengeService;
import com.fitcoach.common.PageResult;
import com.fitcoach.exception.BusinessException;
import com.fitcoach.infra.memory.VectorMemoryService;
import com.fitcoach.user.ProfileExtractionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class SessionService {

    private final SessionRepository sessionRepo;
    /** ObjectProvider 防止循环依赖；ChallengeService 可选下游 */
    private final ObjectProvider<ChallengeService> challengeServiceProvider;
    /** Profile 同样弱依赖 */
    private final ObjectProvider<ProfileExtractionService> profileServiceProvider;
    /** 向量记忆同样弱依赖 */
    private final ObjectProvider<VectorMemoryService> memoryServiceProvider;

    @Transactional
    public Session create(Long userId, Map<String, Object> body) {
        Session s = new Session();
        s.setUserId(userId);
        s.setAction(str(body.get("action"), "unknown"));
        s.setActionLabel(str(body.get("actionLabel"), null));
        s.setReps(intVal(body.get("reps"), 0));
        s.setTargetReps(intVal(body.get("targetReps"), null));
        s.setDuration(intVal(body.get("duration"), 0));
        s.setScore(intVal(body.get("score"), null));
        s.setRhythmScore(intVal(body.get("rhythmScore"), null));
        s.setStabilityScore(intVal(body.get("stabilityScore"), null));
        s.setDepthScore(intVal(body.get("depthScore"), null));
        s.setSymmetryScore(intVal(body.get("symmetryScore"), null));
        s.setCompletionScore(intVal(body.get("completionScore"), null));
        s.setSessionDate(normalizeDate(
                str(body.get("sessionDate"), str(body.get("date"), null))));
        s.setNotes(str(body.get("notes"), null));
        Session saved = sessionRepo.save(s);

        // 钩子：通知挑战赛子系统刷新进度（失败不影响 session 写入）
        try {
            ChallengeService cs = challengeServiceProvider.getIfAvailable();
            if (cs != null) cs.onSessionCreated(userId, saved.getAction(), saved.getReps());
        } catch (Exception e) {
            log.warn("[session] challenge progress sync failed: {}", e.getMessage());
        }
        // 钩子：刷新用户画像（best-effort，失败不影响 session 写入）
        try {
            ProfileExtractionService ps = profileServiceProvider.getIfAvailable();
            if (ps != null) ps.refresh(userId);
        } catch (Exception e) {
            log.warn("[session] profile refresh failed: {}", e.getMessage());
        }
        // 钩子：把本次 session 嵌入到向量记忆
        try {
            VectorMemoryService vm = memoryServiceProvider.getIfAvailable();
            if (vm != null && vm.isEnabled()) {
                vm.addSessionMemory(userId, saved.getId(), describeSession(saved));
            }
        } catch (Exception e) {
            log.warn("[session] vector memory add failed: {}", e.getMessage());
        }
        return saved;
    }

    /** 训练记录的文本化形式（喂给 embedding）。 */
    private static String describeSession(Session s) {
        StringBuilder sb = new StringBuilder();
        sb.append(s.getSessionDate() == null ? "" : s.getSessionDate());
        sb.append(" ").append(s.getActionLabel() == null ? s.getAction() : s.getActionLabel());
        if (s.getReps() != null) sb.append(" ").append(s.getReps()).append("次");
        if (s.getDuration() != null) sb.append(" ").append(s.getDuration()).append("秒");
        if (s.getScore() != null) sb.append(" 得分").append(s.getScore());
        if (s.getNotes() != null && !s.getNotes().isBlank()) sb.append(" 备注: ").append(s.getNotes());
        return sb.toString();
    }

    public PageResult<Session> list(Long userId, int page, int size,
                                     String action, String startDate, String endDate) {
        PageRequest pr = PageRequest.of(Math.max(0, page - 1), size,
                Sort.by(Sort.Direction.DESC, "sessionDate").and(Sort.by(Sort.Direction.DESC, "id")));
        Page<Session> p = sessionRepo.search(userId,
                blank(action) ? null : action,
                blank(startDate) ? null : startDate,
                blank(endDate) ? null : endDate, pr);
        return PageResult.of(p.getContent(), p.getTotalElements(), page, size);
    }

    public Session detail(Long userId, Long id) {
        Session s = sessionRepo.findById(id)
                .orElseThrow(() -> new BusinessException(404, "记录不存在"));
        if (!Objects.equals(s.getUserId(), userId)) throw new BusinessException(403, "无权访问");
        return s;
    }

    @Transactional
    public void updateNotes(Long userId, Long id, Map<String, Object> body) {
        Session s = detail(userId, id);
        if (body.get("notes") instanceof String n) s.setNotes(n);
        sessionRepo.save(s);
    }

    @Transactional
    public void delete(Long userId, Long id) {
        Session s = detail(userId, id);
        sessionRepo.delete(s);
    }

    @Transactional
    public int batch(Long userId, List<Map<String, Object>> list) {
        if (list == null || list.isEmpty()) return 0;
        if (list.size() > 500) throw new BusinessException(400, "单次最多同步 500 条记录");
        int count = 0;
        for (Map<String, Object> body : list) {
            create(userId, body);
            count++;
        }
        return count;
    }

    public String exportCsv(Long userId) {
        List<Session> all = sessionRepo.findByUserIdOrderBySessionDateDesc(userId);
        StringBuilder sb = new StringBuilder();
        sb.append("id,date,action,reps,targetReps,duration,score,rhythm,stability,notes\n");
        for (Session s : all) {
            sb.append(s.getId()).append(',')
              .append(s.getSessionDate() == null ? "" : s.getSessionDate()).append(',')
              .append(s.getAction() == null ? "" : s.getAction()).append(',')
              .append(s.getReps() == null ? 0 : s.getReps()).append(',')
              .append(s.getTargetReps() == null ? "" : s.getTargetReps()).append(',')
              .append(s.getDuration() == null ? 0 : s.getDuration()).append(',')
              .append(s.getScore() == null ? "" : s.getScore()).append(',')
              .append(s.getRhythmScore() == null ? "" : s.getRhythmScore()).append(',')
              .append(s.getStabilityScore() == null ? "" : s.getStabilityScore()).append(',')
              .append(escape(s.getNotes())).append('\n');
        }
        return sb.toString();
    }

    /* ---------- helpers ---------- */

    /**
     * 归一化训练日期为 yyyy-MM-dd（前端离线记录的 date 字段可能带时间），
     * 解析不了就回退今天 — 月历/连续天数/排行榜都依赖纯日期字符串比较。
     */
    private static String normalizeDate(String raw) {
        if (raw == null || raw.isBlank()) return LocalDate.now().toString();
        String d = raw.trim();
        if (d.length() > 10) d = d.substring(0, 10);
        try {
            return LocalDate.parse(d).toString();
        } catch (Exception e) {
            return LocalDate.now().toString();
        }
    }

    private static boolean blank(String s) { return s == null || s.isBlank(); }

    private static String str(Object o, String def) {
        return (o == null) ? def : String.valueOf(o);
    }

    private static Integer intVal(Object o, Integer def) {
        if (o == null) return def;
        if (o instanceof Number n) return n.intValue();
        try { return Integer.parseInt(String.valueOf(o)); } catch (Exception e) { return def; }
    }

    private static String escape(String s) {
        if (s == null) return "";
        String e = s.replace("\"", "\"\"").replace("\n", " ");
        return e.contains(",") ? "\"" + e + "\"" : e;
    }
}
