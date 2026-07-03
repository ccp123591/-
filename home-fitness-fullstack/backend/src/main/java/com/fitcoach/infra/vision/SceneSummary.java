package com.fitcoach.infra.vision;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * vision-svc /scene 返回的畅聊场景摘要 — 与 Python sidecar 的 SceneSummary schema 一致。
 * JoyAI-VL 看视频畅聊抓帧，产出"眼睛看到的"一两句中文描述，注入对话模型上下文。
 * summary 为空 = 视觉不可用/未启用，调用方应跳过注入而不是编造。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SceneSummary {

    /** 1-2 句中文场景描述；空串表示视觉不可用 */
    private String summary;

    /** 画面里是否有人；null 表示未知 */
    private Boolean personPresent;

    /** 实际出摘要的模型标签（'joyai-vl' / 'placeholder-v0'） */
    private String model;
}
