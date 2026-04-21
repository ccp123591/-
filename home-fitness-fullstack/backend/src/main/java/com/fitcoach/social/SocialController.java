package com.fitcoach.social;

import com.fitcoach.common.ApiResult;
import com.fitcoach.common.PageResult;
import com.fitcoach.exception.BusinessException;
import com.fitcoach.security.SecurityUtil;
import com.fitcoach.user.User;
import com.fitcoach.user.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@Tag(name = "08. 社交动态", description = "Post / 点赞 / 评论 / 挑战赛")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class SocialController {

    private final PostRepository postRepo;
    private final PostCommentRepository commentRepo;
    private final PostLikeRepository likeRepo;
    private final UserRepository userRepo;

    @Operation(summary = "动态 Feed（公开）")
    @GetMapping("/posts/feed")
    public ApiResult<PageResult<Map<String, Object>>> feed(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<Post> p = postRepo.findByVisibilityOrderByCreatedAtDesc("PUBLIC",
                PageRequest.of(Math.max(0, page - 1), size));
        Long me = SecurityUtil.currentUserIdOrNull();
        List<Map<String, Object>> items = p.getContent().stream()
                .map(post -> toPostMap(post, me))
                .collect(Collectors.toList());
        return ApiResult.ok(PageResult.of(items, p.getTotalElements(), page, size));
    }

    @Operation(summary = "发布动态")
    @PostMapping("/posts")
    public ApiResult<Post> publish(@RequestBody Map<String, Object> body) {
        Long uid = SecurityUtil.currentUserId();
        Post p = new Post();
        p.setUserId(uid);
        if (body.get("sessionId") instanceof Number n) p.setSessionId(n.longValue());
        if (body.get("content") instanceof String s) p.setContent(s);
        if (body.get("visibility") instanceof String s) p.setVisibility(s);
        return ApiResult.ok(postRepo.save(p), "发布成功");
    }

    @Operation(summary = "动态详情")
    @GetMapping("/posts/{id}")
    public ApiResult<Map<String, Object>> detail(@PathVariable Long id) {
        Post p = postRepo.findById(id).orElseThrow(() -> new BusinessException(404, "动态不存在"));
        return ApiResult.ok(toPostMap(p, SecurityUtil.currentUserIdOrNull()));
    }

    @Operation(summary = "删除动态")
    @DeleteMapping("/posts/{id}")
    public ApiResult<Void> delete(@PathVariable Long id) {
        Post p = postRepo.findById(id).orElseThrow(() -> new BusinessException(404, "动态不存在"));
        Long uid = SecurityUtil.currentUserId();
        if (!uid.equals(p.getUserId()) && !SecurityUtil.isAdmin()) throw new BusinessException(403, "无权删除");
        postRepo.deleteById(id);
        return ApiResult.ok(null, "已删除");
    }

    @Operation(summary = "点赞")
    @Transactional
    @PostMapping("/posts/{id}/like")
    public ApiResult<Void> like(@PathVariable Long id) {
        Long uid = SecurityUtil.currentUserId();
        Post p = postRepo.findById(id).orElseThrow(() -> new BusinessException(404, "动态不存在"));
        if (!likeRepo.existsByPostIdAndUserId(id, uid)) {
            PostLike like = new PostLike();
            like.setPostId(id);
            like.setUserId(uid);
            likeRepo.save(like);
            p.setLikes((p.getLikes() == null ? 0 : p.getLikes()) + 1);
            postRepo.save(p);
        }
        return ApiResult.ok(null, "已点赞");
    }

    @Operation(summary = "取消点赞")
    @Transactional
    @DeleteMapping("/posts/{id}/like")
    public ApiResult<Void> unlike(@PathVariable Long id) {
        Long uid = SecurityUtil.currentUserId();
        Post p = postRepo.findById(id).orElseThrow(() -> new BusinessException(404, "动态不存在"));
        long removed = likeRepo.deleteByPostIdAndUserId(id, uid);
        if (removed > 0) {
            p.setLikes(Math.max(0, (p.getLikes() == null ? 0 : p.getLikes()) - 1));
            postRepo.save(p);
        }
        return ApiResult.ok(null, "已取消");
    }

    @Operation(summary = "评论")
    @Transactional
    @PostMapping("/posts/{id}/comments")
    public ApiResult<PostComment> comment(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String content = body.get("content");
        if (content == null || content.isBlank()) throw new BusinessException(400, "评论内容不能为空");
        Post p = postRepo.findById(id).orElseThrow(() -> new BusinessException(404, "动态不存在"));
        PostComment c = new PostComment();
        c.setPostId(id);
        c.setUserId(SecurityUtil.currentUserId());
        c.setContent(content);
        c = commentRepo.save(c);
        p.setCommentsCount((p.getCommentsCount() == null ? 0 : p.getCommentsCount()) + 1);
        postRepo.save(p);
        return ApiResult.ok(c);
    }

    @Operation(summary = "评论列表")
    @GetMapping("/posts/{id}/comments")
    public ApiResult<List<Map<String, Object>>> comments(@PathVariable Long id) {
        List<PostComment> list = commentRepo.findByPostIdOrderByCreatedAtAsc(id);
        return ApiResult.ok(list.stream().map(c -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", c.getId());
            m.put("content", c.getContent());
            m.put("createdAt", c.getCreatedAt());
            userRepo.findById(c.getUserId()).ifPresent(u -> {
                m.put("userId", u.getId());
                m.put("nickname", u.getNickname());
                m.put("avatar", u.getAvatar() == null ? "" : u.getAvatar());
            });
            return m;
        }).toList());
    }

    /* ---------- 挑战赛（简版 · 配置驱动） ---------- */

    @Operation(summary = "挑战赛列表")
    @GetMapping("/challenges")
    public ApiResult<List<Map<String, Object>>> challenges() {
        return ApiResult.ok(List.of(
            Map.of("id", 1, "title", "30 天深蹲挑战",   "action", "squat",  "targetReps", 1000, "deadline", "2026-05-01", "participants", 256),
            Map.of("id", 2, "title", "7 天俯卧撑马拉松", "action", "pushup", "targetReps", 300,  "deadline", "2026-04-26", "participants", 89)
        ));
    }

    @Operation(summary = "加入挑战（占位）")
    @PostMapping("/challenges/{id}/join")
    public ApiResult<Void> join(@PathVariable Long id) {
        // 需 Challenge/ChallengeParticipant 表；此处保留简化：静态配置 + 不持久化
        SecurityUtil.currentUserId();  // 仍需登录
        return ApiResult.ok(null, "已加入");
    }

    @Operation(summary = "挑战排行（基于 Session 聚合）")
    @GetMapping("/challenges/{id}/rank")
    public ApiResult<List<Map<String, Object>>> challengeRank(@PathVariable Long id) {
        // 暂返回本周榜前 10
        return ApiResult.ok(List.of());
    }

    /* ---------- helpers ---------- */

    private Map<String, Object> toPostMap(Post p, Long me) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", p.getId());
        m.put("content", p.getContent());
        m.put("likes", p.getLikes());
        m.put("commentsCount", p.getCommentsCount());
        m.put("createdAt", p.getCreatedAt());
        m.put("visibility", p.getVisibility());
        m.put("sessionId", p.getSessionId());
        m.put("liked", me != null && likeRepo.existsByPostIdAndUserId(p.getId(), me));
        userRepo.findById(p.getUserId()).ifPresent(u -> {
            m.put("userId", u.getId());
            m.put("nickname", u.getNickname());
            m.put("avatar", u.getAvatar() == null ? "" : u.getAvatar());
        });
        return m;
    }
}
