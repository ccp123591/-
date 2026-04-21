package com.fitcoach.exercise;

import com.fitcoach.common.ApiResult;
import com.fitcoach.exception.BusinessException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "09. 动作库", description = "动作配置管理（前端可读，管理员可写）")
@RestController
@RequestMapping("/api/exercises")
@RequiredArgsConstructor
public class ExerciseController {

    private final ExerciseRepository exerciseRepo;

    @Operation(summary = "动作列表（全部启用）")
    @GetMapping
    public ApiResult<java.util.List<Exercise>> list() {
        return ApiResult.ok(exerciseRepo.findByEnabledTrueOrderBySortOrderAsc());
    }

    @Operation(summary = "动作详情")
    @GetMapping("/{code}")
    public ApiResult<Exercise> detail(@PathVariable String code) {
        return ApiResult.ok(exerciseRepo.findById(code)
                .orElseThrow(() -> new BusinessException(404, "动作不存在")));
    }

    @Operation(summary = "新增动作（管理员）")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ApiResult<Exercise> create(@RequestBody Exercise body) {
        if (body.getCode() == null || body.getCode().isBlank()) throw new BusinessException(400, "code 必填");
        if (exerciseRepo.existsById(body.getCode())) throw new BusinessException(400, "code 已存在");
        if (body.getEnabled() == null) body.setEnabled(true);
        if (body.getSortOrder() == null) body.setSortOrder(99);
        return ApiResult.ok(exerciseRepo.save(body), "已创建");
    }

    @Operation(summary = "更新动作（管理员）")
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{code}")
    public ApiResult<Exercise> update(@PathVariable String code, @RequestBody Map<String, Object> body) {
        Exercise e = exerciseRepo.findById(code)
                .orElseThrow(() -> new BusinessException(404, "动作不存在"));
        if (body.get("name") instanceof String s) e.setName(s);
        if (body.get("description") instanceof String s) e.setDescription(s);
        if (body.get("kind") instanceof String s) e.setKind(s);
        if (body.get("icon") instanceof String s) e.setIcon(s);
        if (body.get("videoUrl") instanceof String s) e.setVideoUrl(s);
        if (body.get("enabled") instanceof Boolean b) e.setEnabled(b);
        if (body.get("sortOrder") instanceof Number n) e.setSortOrder(n.intValue());
        if (body.get("defaultThresholdDown") instanceof Number n) e.setDefaultThresholdDown(n.intValue());
        if (body.get("defaultThresholdUp") instanceof Number n) e.setDefaultThresholdUp(n.intValue());
        return ApiResult.ok(exerciseRepo.save(e), "已更新");
    }

    @Operation(summary = "删除动作（管理员）")
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{code}")
    public ApiResult<Void> delete(@PathVariable String code) {
        if (!exerciseRepo.existsById(code)) throw new BusinessException(404, "动作不存在");
        exerciseRepo.deleteById(code);
        return ApiResult.ok(null, "已删除");
    }
}
