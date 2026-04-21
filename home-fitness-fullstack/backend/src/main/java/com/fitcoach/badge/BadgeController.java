package com.fitcoach.badge;

import com.fitcoach.common.ApiResult;
import com.fitcoach.security.SecurityUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "06. 成就徽章", description = "徽章查询与解锁检测")
@RestController
@RequestMapping("/api/badges")
@RequiredArgsConstructor
public class BadgeController {

    private final BadgeService badgeService;

    @Operation(summary = "徽章总览（带解锁状态）")
    @GetMapping
    public ApiResult<List<Map<String, Object>>> all() {
        return ApiResult.ok(badgeService.allWithMineFlag(SecurityUtil.currentUserIdOrNull()));
    }

    @Operation(summary = "我已解锁的徽章")
    @GetMapping("/mine")
    public ApiResult<List<Map<String, Object>>> mine() {
        return ApiResult.ok(badgeService.mine(SecurityUtil.currentUserId()));
    }

    @Operation(summary = "检测徽章解锁（训练完成后调用）")
    @PostMapping("/check")
    public ApiResult<List<Map<String, Object>>> check() {
        return ApiResult.ok(badgeService.check(SecurityUtil.currentUserId()));
    }
}
