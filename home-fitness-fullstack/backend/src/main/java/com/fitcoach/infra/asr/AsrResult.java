package com.fitcoach.infra.asr;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ASR 输出统一结构。
 * text 为空串表示这段音频没识别出内容（静音/噪声），属正常结果而非错误 —— 前端应静默丢弃并继续听。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AsrResult {
    /** 转写文本；识别不出内容时为空串。 */
    private String text;
    /** mimo */
    private String provider;
    /** 音频时长（秒），由字节数估算，可空。 */
    private Double durationSec;
}
