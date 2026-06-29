package com.fitcoach.infra.vision;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * vision-svc /critique 返回的动作视觉点评 — 与 Python sidecar 的 FormCritique schema 一致。
 * JoyAI-VL 看训练关键帧给出的自然语言反馈；端点未启用时 model=placeholder-v0（通用兜底）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FormCritique {

    /** 动作 code（squat / pushup …） */
    private String action;
    /** 一句话总体评价 */
    private String summary;

    private List<FormIssue> issues;
    private List<String> tips;

    /** 动作标准度 0..100；null 表示未启用视觉点评 */
    private Integer formScore;

    /** 实际出点评的模型标签（'joyai-vl' / 'placeholder-v0'） */
    private String model;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FormIssue {
        private String joint;       // knee / back / hip …
        private String severity;    // minor / major
        private String detail;
    }
}
