package com.fitcoach.session;

import com.fitcoach.common.ApiResult;
import com.fitcoach.common.PageResult;
import com.fitcoach.security.SecurityUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@Tag(name = "03. 训练记录", description = "创建 / 查询 / 同步 / 导出")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/sessions")
@RequiredArgsConstructor
public class SessionController {

    private final SessionService sessionService;

    @Operation(summary = "提交训练记录")
    @PostMapping
    public ApiResult<Session> create(@RequestBody Map<String, Object> body) {
        return ApiResult.ok(sessionService.create(SecurityUtil.currentUserId(), body), "保存成功");
    }

    @Operation(summary = "分页查询训练记录")
    @GetMapping
    public ApiResult<PageResult<Session>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        return ApiResult.ok(sessionService.list(
                SecurityUtil.currentUserId(), page, size, action, startDate, endDate));
    }

    @Operation(summary = "获取记录详情")
    @GetMapping("/{id}")
    public ApiResult<Session> detail(@PathVariable Long id) {
        return ApiResult.ok(sessionService.detail(SecurityUtil.currentUserId(), id));
    }

    @Operation(summary = "更新记录备注")
    @PutMapping("/{id}")
    public ApiResult<Void> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        sessionService.updateNotes(SecurityUtil.currentUserId(), id, body);
        return ApiResult.ok(null, "已更新");
    }

    @Operation(summary = "删除记录")
    @DeleteMapping("/{id}")
    public ApiResult<Void> delete(@PathVariable Long id) {
        sessionService.delete(SecurityUtil.currentUserId(), id);
        return ApiResult.ok(null, "已删除");
    }

    @Operation(summary = "批量同步（离线数据上传）")
    @PostMapping("/batch")
    public ApiResult<Map<String, Object>> batch(@RequestBody Map<String, List<Map<String, Object>>> body) {
        int inserted = sessionService.batch(SecurityUtil.currentUserId(), body.getOrDefault("sessions", List.of()));
        return ApiResult.ok(Map.of("inserted", inserted));
    }

    @Operation(summary = "导出 CSV")
    @GetMapping(value = "/export/csv", produces = "text/csv;charset=UTF-8")
    public ResponseEntity<byte[]> exportCsv() {
        String csv = sessionService.exportCsv(SecurityUtil.currentUserId());
        byte[] body = csv.getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=sessions.csv")
                .body(body);
    }
}
