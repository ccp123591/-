package com.fitcoach.coach;

import com.fitcoach.common.PageResult;
import com.fitcoach.emotion.EmotionService;
import com.fitcoach.emotion.EmotionSummary;
import com.fitcoach.exception.BusinessException;
import com.fitcoach.infra.ai.AiCoachProvider;
import com.fitcoach.infra.ai.ChatAiResponse;
import com.fitcoach.infra.ai.ChatTurn;
import com.fitcoach.infra.ai.CoachAiResponse;
import com.fitcoach.infra.ai.CoachContext;
import com.fitcoach.infra.memory.VectorMemoryService;
import com.fitcoach.infra.vision.FormCritique;
import com.fitcoach.infra.vision.SceneSummary;
import com.fitcoach.infra.vision.VisionClient;
import com.fitcoach.room.RoomLayoutService;
import com.fitcoach.session.Session;
import com.fitcoach.session.SessionRepository;
import com.fitcoach.user.UserProfile;
import com.fitcoach.user.UserProfileRepository;
import com.fitcoach.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;
import java.util.List;

/**
 * AI 教练编排：读取 session → 构造 CoachContext → 调 provider → 持久化 + 返回 FeedbackResponse。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CoachService {

    private static final int RECENT_WINDOW = 7;

    private final SessionRepository sessionRepo;
    private final CoachFeedbackRepository fbRepo;
    private final AiCoachProvider provider;
    private final EmotionService emotionService;
    private final UserRepository userRepo;
    private final VisionClient visionClient;
    /** 弱依赖：profile 缺失（dev 没建表 / module 未加载）不应导致 coach 失败 */
    private final ObjectProvider<UserProfileRepository> profileRepoProvider;
    /** 弱依赖：向量记忆同样可选 */
    private final ObjectProvider<VectorMemoryService> memoryServiceProvider;
    /** 弱依赖：环境扫描可选 — 有扫描记录时把空间摘要注入 prompt */
    private final ObjectProvider<RoomLayoutService> roomServiceProvider;

    @Transactional
    public FeedbackResponse feedback(Long userId, Long sessionId) {
        return feedback(userId, sessionId, null);
    }

    /**
     * 训练后反馈。formReview 为本次动作的视觉点评摘要（JoyAI-VL，可空）——
     * 注入上下文后，AI 把"看到的"和"数据算到的"结合成一段反馈。
     */
    @Transactional
    public FeedbackResponse feedback(Long userId, Long sessionId, String formReview) {
        Session s = sessionRepo.findById(sessionId)
                .orElseThrow(() -> new BusinessException(404, "训练记录不存在"));
        if (!s.getUserId().equals(userId)) {
            throw new BusinessException(403, "无权访问该训练记录");
        }

        CoachContext ctx = buildContext(userId, s);
        if (formReview != null && !formReview.isBlank()) {
            ctx.setFormReview(formReview.strip());
        }
        CoachAiResponse ai = provider.feedback(ctx);

        CoachFeedback saved = persist(userId, sessionId, ai);
        return toResponse(saved, ai);
    }

    @Transactional
    public FeedbackResponse suggestion(Long userId) {
        CoachContext ctx = buildContext(userId, null);
        CoachAiResponse ai = provider.suggestion(ctx);
        CoachFeedback saved = persist(userId, null, ai);
        return toResponse(saved, ai);
    }

    @Transactional
    public FeedbackResponse weeklyPlan(Long userId) {
        CoachContext ctx = buildContext(userId, null);
        CoachAiResponse ai = provider.weeklyPlan(ctx);
        CoachFeedback saved = persist(userId, null, ai);
        return toResponse(saved, ai);
    }

    /**
     * 动作视觉点评 — 把训练关键帧交给 vision-svc(JoyAI-VL) 给自然语言反馈。
     * 即时反馈，不落库；vision-svc 端点未启用时返回通用兜底（model=placeholder-v0）。
     */
    public FormCritique formCritique(Long userId, String action, Integer reps, Integer score,
                                     List<MultipartFile> frames) {
        FormCritique fc = visionClient.critique(action, reps, score, frames);
        log.info("[coach] form-critique user={} action={} model={} score={}",
                userId, action, fc == null ? null : fc.getModel(), fc == null ? null : fc.getFormScore());
        return fc;
    }

    @Transactional(readOnly = true)
    public PageResult<FeedbackResponse> history(Long userId, int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(size, 100));
        Page<CoachFeedback> p = fbRepo.findByUserIdOrderByCreatedAtDesc(userId,
                PageRequest.of(safePage, safeSize));
        List<FeedbackResponse> items = p.getContent().stream().map(this::toResponse).toList();
        return PageResult.of(items, p.getTotalElements(), safePage, safeSize);
    }

    /**
     * 视频畅聊场景摘要 — 把畅聊抓帧交给 vision-svc(JoyAI-VL) 描述"用户在干嘛"。
     * 即时调用，不落库；返回的 summary 为空表示视觉不可用，前端应跳过注入。
     */
    public SceneSummary scene(Long userId, List<MultipartFile> frames) {
        SceneSummary s = visionClient.scene(frames);
        log.info("[coach] scene user={} model={} person={}",
                userId, s == null ? null : s.getModel(), s == null ? null : s.getPersonPresent());
        return s;
    }

    /** 陪伴聊天（无画面）。 */
    public ChatResponse chat(Long userId, String message, List<ChatTurn> history) {
        return chat(userId, message, history, null);
    }

    /**
     * 陪伴聊天：把当前消息当 RAG query 召回相关记忆，注入 ctx 后调 provider，
     * 然后把"用户说 / 我回了"两条都写回记忆库 — 下次再聊就能被唤醒。
     * sceneSummary 为视频畅聊抓帧的场景摘要（JoyAI-VL，可空）——注入后 AI "看得见"用户。
     */
    public ChatResponse chat(Long userId, String message, List<ChatTurn> history, String sceneSummary) {
        if (message == null || message.isBlank()) {
            throw new BusinessException(400, "消息不能为空");
        }
        if (message.length() > 1000) {
            throw new BusinessException(400, "单条消息过长，请控制在 1000 字以内");
        }

        CoachContext ctx = buildContext(userId, null);
        if (sceneSummary != null && !sceneSummary.isBlank()) {
            ctx.setSceneSummary(sceneSummary.strip());
        }

        // 用「当前消息」作为 RAG query — 比 buildContext 默认 query 更精准
        String recalled = "";
        VectorMemoryService vm = memoryServiceProvider.getIfAvailable();
        if (vm != null && vm.isEnabled()) {
            try {
                recalled = vm.recall(userId, message, 5);
                if (recalled != null && !recalled.isBlank()) {
                    ctx.setRelevantHistory(recalled);
                }
            } catch (Exception e) {
                log.warn("[chat] recall 失败: {}", e.getMessage());
            }
        }

        ChatAiResponse ai;
        try {
            ai = provider.chat(ctx, message, history);
        } catch (BusinessException be) {
            throw be;
        } catch (Exception e) {
            log.warn("[chat] provider 失败: {}", e.getMessage());
            throw new BusinessException(503, "AI 教练暂时不可用，请稍后再试");
        }

        // 双向落库：用户问题 + AI 回答都进向量记忆，下次能唤起
        if (vm != null && vm.isEnabled()) {
            try {
                vm.addChatMemory(userId, "用户说：" + message);
                if (ai.getReply() != null && !ai.getReply().isBlank()) {
                    vm.addChatMemory(userId, "小柯回了：" + ai.getReply());
                }
            } catch (Exception ignored) {
                /* 记忆写入失败不影响回复 */
            }
        }

        return ChatResponse.builder()
                .reply(ai.getReply())
                .provider(ai.getProvider())
                .tokensUsed(ai.getTokensUsed())
                .recalled(splitRecall(recalled))
                .build();
    }

    private static List<String> splitRecall(String recalled) {
        if (recalled == null || recalled.isBlank()) return List.of();
        return Arrays.stream(recalled.split("\n"))
                .map(s -> s.replaceFirst("^-\\s*\\[[^\\]]*\\]\\s*", "").replaceFirst("^-\\s*", "").trim())
                .filter(s -> !s.isBlank())
                .toList();
    }

    /**
     * 叙旧模式：按时间近拉取最近的聊天记忆，让 AI 像老朋友一样把它们串起来温柔讲出来。
     * 不绑 message — 是用户主动点"叙旧"触发的。
     */
    public ChatResponse reminisce(Long userId) {
        VectorMemoryService vm = memoryServiceProvider.getIfAvailable();
        String recent = "";
        if (vm != null && vm.isEnabled()) {
            try {
                recent = vm.recentChat(userId, 12);
            } catch (Exception e) {
                log.warn("[reminisce] recentChat 失败: {}", e.getMessage());
            }
        }

        if (recent == null || recent.isBlank()) {
            return ChatResponse.builder()
                    .reply("我们最近还没怎么聊过呢，多说几句话给我，下回就能跟你叙旧啦。")
                    .provider("mock")
                    .tokensUsed(0)
                    .recalled(List.of())
                    .build();
        }

        CoachContext ctx = buildContext(userId, null);
        ctx.setRelevantHistory(recent);

        // 用半结构化的"叙旧"用户消息触发 — 比纯 system override 更通用，Mock 也吃这个 keyword
        String userMsg = "（叙旧）跟我把我们最近聊过的事儿温柔地串起来讲一下吧，像老朋友一样自然。";

        ChatAiResponse ai;
        try {
            ai = provider.chat(ctx, userMsg, List.of());
        } catch (BusinessException be) {
            throw be;
        } catch (Exception e) {
            log.warn("[reminisce] provider 失败: {}", e.getMessage());
            throw new BusinessException(503, "AI 教练暂时不可用，请稍后再试");
        }

        // 把这次叙旧自己也存进记忆 — 下次能引用"上次叙旧时我说过..."
        if (vm != null && vm.isEnabled() && ai.getReply() != null && !ai.getReply().isBlank()) {
            try { vm.addChatMemory(userId, "小柯叙旧：" + ai.getReply()); } catch (Exception ignored) {}
        }

        return ChatResponse.builder()
                .reply(ai.getReply())
                .provider(ai.getProvider())
                .tokensUsed(ai.getTokensUsed())
                .recalled(splitRecall(recent))
                .build();
    }

    // —— helpers ——

    private CoachContext buildContext(Long userId, Session current) {
        List<Session> recent = sessionRepo.findByUserIdOrderBySessionDateDesc(userId);
        List<Session> window = recent.size() > RECENT_WINDOW ? recent.subList(0, RECENT_WINDOW) : recent;

        long totalReps = window.stream().mapToInt(s -> s.getReps() == null ? 0 : s.getReps()).sum();
        int avgScore = window.isEmpty() ? 0 : (int) Math.round(
                window.stream().mapToInt(s -> s.getScore() == null ? 0 : s.getScore()).average().orElse(0));

        List<CoachContext.RecentSession> recentDtos = window.stream()
                .map(s -> CoachContext.RecentSession.builder()
                        .date(s.getSessionDate())
                        .action(s.getAction())
                        .reps(s.getReps())
                        .score(s.getScore())
                        .build())
                .toList();

        CoachContext.CoachContextBuilder b = CoachContext.builder()
                .userId(userId)
                .recentAvgScore(avgScore)
                .recentTotalReps(totalReps)
                .recentSessions(recentDtos);

        // 注入用户昵称（个性化称呼；查询失败不影响主流程）
        try {
            userRepo.findById(userId).ifPresent(u -> b.nickname(u.getNickname()));
        } catch (Exception ignored) {
        }

        // 注入最近一次环境扫描摘要（弱依赖；没扫描过/异常都跳过）
        try {
            RoomLayoutService rs = roomServiceProvider.getIfAvailable();
            if (rs != null) {
                String room = rs.latestSummaryForCoach(userId);
                if (room != null && !room.isBlank()) b.roomSummary(room);
            }
        } catch (Exception ignored) {
        }

        // 注入最近 7 天情感（失败/无数据则跳过 — 不影响 coach 主流程）
        try {
            EmotionSummary es = emotionService.summary(userId, 7);
            if (es != null && es.getTotal() > 0) {
                b.recentDominantEmotion(es.getDominantEmotion())
                        .recentEmotionScore(es.getAvgScore());
            }
        } catch (Exception ignored) {
            // emotion 是可选输入，AI coach 不应因之失败
        }

        // 注入用户画像摘要（弱依赖；缺失或异常都跳过）
        try {
            UserProfileRepository repo = profileRepoProvider.getIfAvailable();
            if (repo != null) {
                repo.findByUserId(userId).map(UserProfile::getSummaryText)
                        .ifPresent(b::userProfileSummary);
            }
        } catch (Exception ignored) {
        }

        // 注入 RAG 召回（弱依赖；缺失或异常都跳过）
        try {
            VectorMemoryService vm = memoryServiceProvider.getIfAvailable();
            if (vm != null && vm.isEnabled()) {
                String query = current != null
                        ? (current.getActionLabel() != null ? current.getActionLabel() : current.getAction())
                        : "训练";
                String recall = vm.recall(userId, query, 3);
                if (recall != null && !recall.isBlank()) {
                    b.relevantHistory(recall);
                }
            }
        } catch (Exception ignored) {
        }

        if (current != null) {
            b.action(current.getAction())
                    .actionLabel(current.getActionLabel())
                    .reps(current.getReps())
                    .targetReps(current.getTargetReps())
                    .duration(current.getDuration())
                    .score(current.getScore())
                    .rhythmScore(current.getRhythmScore())
                    .stabilityScore(current.getStabilityScore())
                    .depthScore(current.getDepthScore())
                    .symmetryScore(current.getSymmetryScore())
                    .completionScore(current.getCompletionScore());
        }
        return b.build();
    }

    private CoachFeedback persist(Long userId, Long sessionId, CoachAiResponse ai) {
        CoachFeedback fb = new CoachFeedback();
        fb.setUserId(userId);
        fb.setSessionId(sessionId);
        fb.setReview(ai.getReview());
        fb.setSuggestion(ai.getSuggestion());
        fb.setEncouragement(ai.getEncouragement());
        fb.setNextGoal(ai.getNextGoal());
        fb.setProvider(ai.getProvider() != null ? ai.getProvider() : provider.name());
        fb.setTokensUsed(ai.getTokensUsed() == null ? 0 : ai.getTokensUsed());
        return fbRepo.save(fb);
    }

    private FeedbackResponse toResponse(CoachFeedback fb, CoachAiResponse ai) {
        return FeedbackResponse.builder()
                .id(fb.getId())
                .review(ai.getReview())
                .suggestion(ai.getSuggestion())
                .encouragement(ai.getEncouragement())
                .nextGoal(ai.getNextGoal())
                .provider(fb.getProvider())
                .tokensUsed(fb.getTokensUsed())
                .createdAt(fb.getCreatedAt())
                .build();
    }

    private FeedbackResponse toResponse(CoachFeedback fb) {
        return FeedbackResponse.builder()
                .id(fb.getId())
                .review(fb.getReview())
                .suggestion(fb.getSuggestion())
                .encouragement(fb.getEncouragement())
                .nextGoal(fb.getNextGoal())
                .provider(fb.getProvider())
                .tokensUsed(fb.getTokensUsed())
                .createdAt(fb.getCreatedAt())
                .build();
    }
}
