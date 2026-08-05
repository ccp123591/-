package com.fitcoach.infra.asr;

/**
 * 语音转文字（ASR）抽象。
 *
 * 存在的原因：浏览器原生 Web Speech API 在移动端不可用 —— 它只是外壳，真正的识别
 * 引擎来自平台（桌面 Chrome 走 Google 云端；Android Chrome 依赖设备上的 GMS/Google App；
 * iOS 仅 Safari 本体有 Apple 听写；微信等 WKWebView/X5 内核完全不提供）。
 * 国行 Android 无 GMS，识别引擎缺失，start() 后既无 onresult 也无 onerror。
 * 把识别搬到服务端后，设备只负责录音，能力不再依赖平台。
 *
 * 实现：
 *   MimoAsrProvider — 调小米 MiMo 云端 API（mimo-v2.5-asr）
 * 由 ai.asr.provider 配置选择；不可用时 AsrService 抛 503，前端降级为文字输入。
 */
public interface AsrProvider {

    String name();

    /**
     * 音频转文字。
     *
     * @param audio  音频字节
     * @param format 容器格式，仅 wav / mp3（受上游网关白名单限制）
     * @return 转写结果；识别不出内容时 text 为空串而非 null
     */
    AsrResult transcribe(byte[] audio, String format);

    /** 服务是否可用（api-key 已配置）。 */
    boolean isAvailable();
}
