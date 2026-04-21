package com.fitcoach.coach;

import com.fitcoach.common.ApiResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 教练 Controller
 * 未来将调用 Claude Haiku 4.5 API 生成个性化反馈
 * 使用 Prompt Caching 降本：系统 prompt + 动作定义缓存，命中率 > 90%
 */
@Tag(name = "04. AI 教练", description = "Claude 驱动的智能点评与建议")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/coach")
public class CoachController {

    @Operation(summary = "生成训练后反馈")
    @PostMapping("/feedback")
    public ApiResult<Map<String, Object>> feedback(@RequestBody Map<String, Object> body) {
        // TODO: 查询 session 详情 → 拼装 prompt → 调 Claude → 持久化
        Map<String, Object> r = new HashMap<>();
        r.put("review", "整体完成得不错，动作节奏稳定，继续保持！");
        r.put("suggestion", "下次尝试加大蹲幅，并注意左右发力均衡");
        r.put("encouragement", "坚持就是胜利，明天也要继续哦！");
        r.put("nextGoal", "建议下一次目标：12 次 深蹲");
        r.put("provider", "mock");
        return ApiResult.ok(r);
    }

    @Operation(summary = "获取综合训练建议（基于近 7 次）")
    @GetMapping("/suggestion")
    public ApiResult<Map<String, Object>> suggestion() {
        Map<String, Object> r = new HashMap<>();
        r.put("summary", "近期训练稳定，节奏有明显进步");
        r.put("advice", List.of("加强上肢训练", "每天增加 5 分钟有氧", "保持周 3 次训练频率"));
        return ApiResult.ok(r);
    }

    @Operation(summary = "生成本周训练计划")
    @GetMapping("/weekly-plan")
    public ApiResult<Map<String, Object>> weeklyPlan() {
        Map<String, Object> r = new HashMap<>();
        r.put("week", "2026-W16");
        r.put("days", List.of(
            Map.of("day", "周一", "action", "深蹲", "reps", 15),
            Map.of("day", "周三", "action", "俯卧撑", "reps", 10),
            Map.of("day", "周五", "action", "平板支撑", "duration", 60),
            Map.of("day", "周日", "action", "前屈伸展", "reps", 20)
        ));
        return ApiResult.ok(r);
    }

    @Operation(summary = "历史反馈列表")
    @GetMapping("/history")
    public ApiResult<List<Map<String, Object>>> history() {
        return ApiResult.ok(List.of());
    }
}
