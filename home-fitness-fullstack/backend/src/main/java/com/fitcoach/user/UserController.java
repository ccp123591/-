package com.fitcoach.user;

import com.fitcoach.common.ApiResult;
import com.fitcoach.security.SecurityUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Tag(name = "02. 用户", description = "个人资料 / 统计 / 关注")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @Operation(summary = "获取当前用户资料")
    @GetMapping("/me")
    public ApiResult<Map<String, Object>> me() {
        return ApiResult.ok(userService.profile(SecurityUtil.currentUserId()));
    }

    @Operation(summary = "更新个人资料")
    @PutMapping("/me")
    public ApiResult<Void> updateProfile(@RequestBody Map<String, Object> body) {
        userService.updateProfile(SecurityUtil.currentUserId(), body);
        return ApiResult.ok(null, "已更新");
    }

    @Operation(summary = "上传头像")
    @PostMapping("/me/avatar")
    public ApiResult<Map<String, String>> uploadAvatar(@RequestParam("avatar") MultipartFile file) throws IOException {
        String url = userService.saveAvatar(SecurityUtil.currentUserId(), file);
        Map<String, String> r = new HashMap<>();
        r.put("url", url);
        return ApiResult.ok(r);
    }

    @Operation(summary = "个人训练统计")
    @GetMapping("/me/stats")
    public ApiResult<Map<String, Object>> stats() {
        return ApiResult.ok(userService.stats(SecurityUtil.currentUserId()));
    }

    @Operation(summary = "获取月历日签到数据")
    @GetMapping("/me/calendar")
    public ApiResult<List<Map<String, Object>>> calendar(@RequestParam(required = false) String yearMonth) {
        return ApiResult.ok(userService.calendar(SecurityUtil.currentUserId(), yearMonth));
    }

    @Operation(summary = "关注用户")
    @PostMapping("/{userId}/follow")
    public ApiResult<Void> follow(@PathVariable Long userId) {
        userService.follow(SecurityUtil.currentUserId(), userId);
        return ApiResult.ok(null, "已关注");
    }

    @Operation(summary = "取消关注")
    @DeleteMapping("/{userId}/follow")
    public ApiResult<Void> unfollow(@PathVariable Long userId) {
        userService.unfollow(SecurityUtil.currentUserId(), userId);
        return ApiResult.ok(null, "已取消");
    }

    @Operation(summary = "我的粉丝")
    @GetMapping("/{userId}/followers")
    public ApiResult<List<Map<String, Object>>> followers(@PathVariable Long userId) {
        return ApiResult.ok(userService.followers(userId));
    }

    @Operation(summary = "我的关注")
    @GetMapping("/{userId}/followings")
    public ApiResult<List<Map<String, Object>>> followings(@PathVariable Long userId) {
        return ApiResult.ok(userService.followings(userId));
    }
}
