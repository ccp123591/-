package com.fitcoach.session;

import com.fitcoach.common.PageResult;
import com.fitcoach.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SessionService {

    private final SessionRepository sessionRepo;

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
        s.setSessionDate(str(body.get("sessionDate"), LocalDate.now().toString()));
        s.setNotes(str(body.get("notes"), null));
        return sessionRepo.save(s);
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
