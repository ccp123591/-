package com.fitcoach.social;

import com.fitcoach.common.ApiResult;
import com.fitcoach.common.PageResult;
import com.fitcoach.exception.BusinessException;
import com.fitcoach.security.SecurityUtil;
import com.fitcoach.user.User;
import com.fitcoach.user.UserFollowRepository;
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
    private final UserFollowRepository followRepo;

    @Operation(summary = "动态 Feed（公开）")
    @GetMapping("/posts/feed")
    public ApiResult<PageResult<Map<String, Object>>> feed(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        int safePage = Math.max(1, page);
        int safeSize = Math.max(1, Math.min(size, 100));
        Page<Post> p = postRepo.findByVisibilityOrderByCreatedAtDesc("PUBLIC",
                PageRequest.of(safePage - 1, safeSize));
        Long me = SecurityUtil.currentUserIdOrNull();
        List<Post> posts = p.getContent();
        Map<Long, User> users = usersByIds(posts.stream().map(Post::getUserId).toList());
        Set<Long> liked = me == null ? Set.of()
                : likeRepo.findByUserIdAndPostIdIn(me, posts.stream().map(Post::getId).toList())
                        .stream().map(PostLike::getPostId).collect(Collectors.toSet());
        List<Map<String, Object>> items = posts.stream()
                .map(post -> postMap(post, users.get(post.getUserId()), liked.contains(post.getId())))
                .collect(Collectors.toList());
        return ApiResult.ok(PageResult.of(items, p.getTotalElements(), safePage, safeSize));
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
        Long me = SecurityUtil.currentUserIdOrNull();
        requireCanView(p, me);
        return ApiResult.ok(postMap(p, userRepo.findById(p.getUserId()).orElse(null),
                me != null && likeRepo.existsByPostIdAndUserId(id, me)));
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
            postRepo.incrementLikes(id);
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
            postRepo.decrementLikes(id);
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
        postRepo.incrementComments(id);
        return ApiResult.ok(c);
    }

    @Operation(summary = "评论列表")
    @GetMapping("/posts/{id}/comments")
    public ApiResult<List<Map<String, Object>>> comments(@PathVariable Long id) {
        Post post = postRepo.findById(id).orElseThrow(() -> new BusinessException(404, "Post not found"));
        requireCanView(post, SecurityUtil.currentUserIdOrNull());
        List<PostComment> list = commentRepo.findByPostIdOrderByCreatedAtAsc(id);
        Map<Long, User> users = usersByIds(list.stream().map(PostComment::getUserId).toList());
        return ApiResult.ok(list.stream().map(c -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", c.getId());
            m.put("content", c.getContent());
            m.put("createdAt", c.getCreatedAt());
            User u = users.get(c.getUserId());
            if (u != null) {
                m.put("userId", u.getId());
                m.put("nickname", u.getNickname());
                m.put("avatar", u.getAvatar() == null ? "" : u.getAvatar());
            }
            return m;
        }).toList());
    }

    /* ---------- 挑战赛端点已迁移至 ChallengeController（Phase 5 T24） ---------- */

    /* ---------- helpers ---------- */

    private Map<Long, User> usersByIds(Collection<Long> ids) {
        Map<Long, User> m = new HashMap<>();
        userRepo.findAllById(ids).forEach(u -> m.put(u.getId(), u));
        return m;
    }

    private void requireCanView(Post post, Long viewerId) {
        String visibility = post.getVisibility() == null ? "PUBLIC" : post.getVisibility();
        if ("PUBLIC".equals(visibility)) return;
        if (viewerId != null && viewerId.equals(post.getUserId())) return;
        if ("FOLLOWERS".equals(visibility) && viewerId != null
                && followRepo.existsByFollowerIdAndFollowingId(viewerId, post.getUserId())) return;
        throw new BusinessException(403, "Post is not visible to current user");
    }

    private Map<String, Object> postMap(Post p, User u, boolean liked) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", p.getId());
        m.put("content", p.getContent());
        m.put("likes", p.getLikes());
        m.put("commentsCount", p.getCommentsCount());
        m.put("createdAt", p.getCreatedAt());
        m.put("visibility", p.getVisibility());
        m.put("sessionId", p.getSessionId());
        m.put("liked", liked);
        if (u != null) {
            m.put("userId", u.getId());
            m.put("nickname", u.getNickname());
            m.put("avatar", u.getAvatar() == null ? "" : u.getAvatar());
        }
        return m;
    }
}
