package com.fitcoach.infra.ai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 拼装给 AI 的上下文。null 字段允许 — 由 prompt 模板按存在性裁剪。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CoachContext {

    private Long userId;
    private String nickname;

    // —— 本次 session（feedback 用） ——
    private String action;
    private String actionLabel;
    private Integer reps;
    private Integer targetReps;
    private Integer duration;
    private Integer score;
    private Integer rhythmScore;
    private Integer stabilityScore;
    private Integer depthScore;
    private Integer symmetryScore;
    private Integer completionScore;

    // —— 近期聚合（suggestion / weeklyPlan 用） ——
    private Integer recentAvgScore;
    private Long recentTotalReps;
    private List<RecentSession> recentSessions;

    // —— 近期情感（emotion module 注入；缺省 null 表示无数据） ——
    /** 近 7 天主导情感：positive / neutral / negative */
    private String recentDominantEmotion;
    /** 近 7 天平均情感得分 (-1..1) */
    private Double recentEmotionScore;

    // —— 用户画像（profile module 注入；缺省 null） ——
    /** 由 ProfileExtractionService 产出的中文 1 句话画像。 */
    private String userProfileSummary;

    // —— RAG 召回（vector memory module 注入；缺省 null） ——
    /** Top-K 历史训练记忆，用于 prompt 中的相关上下文。 */
    private String relevantHistory;

    // —— 训练环境（room module 注入；缺省 null） ——
    /** 最近一次环境扫描的中文摘要，如"客厅约 8.5㎡，光线良好，右侧 1.2m 有沙发"。 */
    private String roomSummary;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RecentSession {
        private String date;
        private String action;
        private Integer reps;
        private Integer score;
    }
}
