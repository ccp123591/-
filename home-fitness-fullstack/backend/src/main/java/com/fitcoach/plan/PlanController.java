package com.fitcoach.plan;

import com.fitcoach.common.ApiResult;
import com.fitcoach.common.PageResult;
import com.fitcoach.exception.BusinessException;
import com.fitcoach.security.SecurityUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Tag(name = "05. 训练计划", description = "官方计划 / 我的计划 / 计划市场")
@RestController
@RequestMapping("/api/plans")
@RequiredArgsConstructor
public class PlanController {

    private final PlanRepository planRepo;
    private final UserPlanRepository userPlanRepo;

    @Operation(summary = "计划列表（分页，全部已发布）")
    @GetMapping
    public ApiResult<PageResult<Plan>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pr = PageRequest.of(Math.max(0, page - 1), size,
                Sort.by(Sort.Direction.DESC, "adoptCount"));
        Page<Plan> p = planRepo.findAll(pr);
        return ApiResult.ok(PageResult.of(p.getContent(), p.getTotalElements(), page, size));
    }

    @Operation(summary = "官方推荐计划")
    @GetMapping("/official")
    public ApiResult<List<Plan>> official() {
        return ApiResult.ok(planRepo.findByOfficialTrueAndPublishedTrueOrderByAdoptCountDesc());
    }

    @Operation(summary = "计划市场（用户共享）")
    @GetMapping("/market")
    public ApiResult<PageResult<Plan>> market(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pr = PageRequest.of(Math.max(0, page - 1), size,
                Sort.by(Sort.Direction.DESC, "adoptCount"));
        Page<Plan> p = planRepo.findAll(pr);
        return ApiResult.ok(PageResult.of(
                p.getContent().stream().filter(pl -> Boolean.FALSE.equals(pl.getOfficial())).toList(),
                p.getTotalElements(), page, size));
    }

    @Operation(summary = "计划详情")
    @GetMapping("/{id}")
    public ApiResult<Plan> detail(@PathVariable Long id) {
        return ApiResult.ok(planRepo.findById(id)
                .orElseThrow(() -> new BusinessException(404, "计划不存在")));
    }

    @Operation(summary = "创建训练计划（用户）")
    @PostMapping
    public ApiResult<Plan> create(@RequestBody Map<String, Object> body) {
        Long uid = SecurityUtil.currentUserId();
        Plan p = Plan.builder()
                .title(str(body.get("title"), "未命名计划"))
                .description(str(body.get("description"), ""))
                .level(str(body.get("level"), "NEWBIE"))
                .cover(str(body.get("cover"), "#7C6AFF"))
                .days(intVal(body.get("days"), 7))
                .itemsJson(str(body.get("itemsJson"), "[]"))
                .official(false)
                .published(true)
                .authorId(uid)
                .adoptCount(0)
                .build();
        return ApiResult.ok(planRepo.save(p), "计划已创建");
    }

    @Operation(summary = "更新计划")
    @PutMapping("/{id}")
    public ApiResult<Void> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Plan p = planRepo.findById(id).orElseThrow(() -> new BusinessException(404, "计划不存在"));
        Long uid = SecurityUtil.currentUserId();
        if (!SecurityUtil.isAdmin() && !uid.equals(p.getAuthorId())) throw new BusinessException(403, "无权修改");
        if (body.get("title") instanceof String s) p.setTitle(s);
        if (body.get("description") instanceof String s) p.setDescription(s);
        if (body.get("level") instanceof String s) p.setLevel(s);
        if (body.get("cover") instanceof String s) p.setCover(s);
        if (body.get("days") instanceof Number n) p.setDays(n.intValue());
        if (body.get("itemsJson") instanceof String s) p.setItemsJson(s);
        if (body.get("published") instanceof Boolean b) p.setPublished(b);
        planRepo.save(p);
        return ApiResult.ok(null, "已更新");
    }

    @Operation(summary = "删除计划")
    @DeleteMapping("/{id}")
    public ApiResult<Void> delete(@PathVariable Long id) {
        Plan p = planRepo.findById(id).orElseThrow(() -> new BusinessException(404, "计划不存在"));
        Long uid = SecurityUtil.currentUserId();
        if (!SecurityUtil.isAdmin() && !uid.equals(p.getAuthorId())) throw new BusinessException(403, "无权删除");
        planRepo.deleteById(id);
        return ApiResult.ok(null, "已删除");
    }

    @Operation(summary = "我采用的计划")
    @GetMapping("/mine")
    public ApiResult<List<Map<String, Object>>> mine() {
        Long uid = SecurityUtil.currentUserId();
        List<UserPlan> ups = userPlanRepo.findByUserIdAndStatus(uid, "ACTIVE");
        return ApiResult.ok(ups.stream().map(up -> {
            Map<String, Object> m = new HashMap<>();
            m.put("planId", up.getPlanId());
            m.put("progressDay", up.getProgressDay());
            m.put("status", up.getStatus());
            m.put("adoptedAt", up.getAdoptedAt());
            planRepo.findById(up.getPlanId()).ifPresent(p -> {
                m.put("title", p.getTitle());
                m.put("days", p.getDays());
                m.put("cover", p.getCover());
                m.put("level", p.getLevel());
            });
            return m;
        }).collect(Collectors.toList()));
    }

    @Operation(summary = "采用计划")
    @Transactional
    @PostMapping("/{id}/adopt")
    public ApiResult<Void> adopt(@PathVariable Long id) {
        Long uid = SecurityUtil.currentUserId();
        Plan plan = planRepo.findById(id).orElseThrow(() -> new BusinessException(404, "计划不存在"));
        if (userPlanRepo.findByUserIdAndPlanId(uid, id).isPresent()) {
            return ApiResult.ok(null, "已在进行中");
        }
        UserPlan up = new UserPlan();
        up.setUserId(uid);
        up.setPlanId(id);
        up.setProgressDay(0);
        up.setStatus("ACTIVE");
        userPlanRepo.save(up);
        plan.setAdoptCount((plan.getAdoptCount() == null ? 0 : plan.getAdoptCount()) + 1);
        planRepo.save(plan);
        return ApiResult.ok(null, "已采用");
    }

    @Operation(summary = "放弃计划")
    @Transactional
    @DeleteMapping("/{id}/adopt")
    public ApiResult<Void> abandon(@PathVariable Long id) {
        Long uid = SecurityUtil.currentUserId();
        userPlanRepo.findByUserIdAndPlanId(uid, id).ifPresent(up -> {
            up.setStatus("ABANDONED");
            up.setUpdatedAt(LocalDateTime.now());
            userPlanRepo.save(up);
        });
        return ApiResult.ok(null, "已放弃");
    }

    @Operation(summary = "更新计划进度")
    @PutMapping("/{id}/progress")
    public ApiResult<Void> progress(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Long uid = SecurityUtil.currentUserId();
        UserPlan up = userPlanRepo.findByUserIdAndPlanId(uid, id)
                .orElseThrow(() -> new BusinessException(404, "未采用此计划"));
        if (body.get("progressDay") instanceof Number n) up.setProgressDay(n.intValue());
        if (body.get("status") instanceof String s) up.setStatus(s);
        up.setUpdatedAt(LocalDateTime.now());
        userPlanRepo.save(up);
        return ApiResult.ok(null, "进度已更新");
    }

    private static String str(Object o, String def) { return o == null ? def : String.valueOf(o); }
    private static Integer intVal(Object o, Integer def) {
        if (o == null) return def;
        if (o instanceof Number n) return n.intValue();
        try { return Integer.parseInt(String.valueOf(o)); } catch (Exception e) { return def; }
    }
}
