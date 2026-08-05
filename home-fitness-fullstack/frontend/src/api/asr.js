import client from './client';

/**
 * ASR — 服务端语音识别。
 *
 * 移动端浏览器没有可用的原生识别引擎（国行 Android 无 GMS、iOS 仅 Safari 本体、
 * 微信等 WKWebView 完全没有），因此手机端的语音输入只能走这条路。
 * 音频由 wavRecorder 采集并编码为 WAV —— 上游网关只认 wav / mp3。
 */
export const asrApi = {
  /** 服务端识别是否可用；false 时前端应降级为文字输入。 */
  status: () => client.get('/asr/status'),

  /**
   * 上传一段 WAV 做转写。
   * @param {Blob} wavBlob wavRecorder.encodeWav 的产物
   * @returns {Promise<{text:string, provider:string, durationSec:number}>}
   */
  transcribe: (wavBlob) => {
    const form = new FormData();
    form.append('file', wavBlob, 'speech.wav');
    return client.post('/asr/transcribe', form, {
      headers: { 'Content-Type': 'multipart/form-data' },
      // 覆盖 client 默认 15s：移动端弱网下音频上传 + 云端识别可能更久
      timeout: 30000
    });
  }
};
