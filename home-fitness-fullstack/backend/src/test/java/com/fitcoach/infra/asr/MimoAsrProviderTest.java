package com.fitcoach.infra.asr;

import com.fitcoach.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.client.RestClientTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.client.MockRestServiceServer;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@RestClientTest(MimoAsrProvider.class)
@TestPropertySource(properties = {
        "ai.asr.provider=mimo",
        "ai.asr.mimo.api-key=test-key",
        "ai.asr.mimo.base-url=https://token-plan-cn.xiaomimimo.com/v1",
        "ai.asr.mimo.model=mimo-v2.5-asr"
})
class MimoAsrProviderTest {

    private static final String URL = "https://token-plan-cn.xiaomimimo.com/v1/chat/completions";

    @Autowired
    MimoAsrProvider provider;

    @Autowired
    MockRestServiceServer server;

    /** 构造一个最小合法 WAV（44 字节头 + PCM），byteRate 按 16kHz/16bit/单声道 = 32000。 */
    private static byte[] wav(int pcmBytes) {
        int byteRate = 32000;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteBuffer header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN);
        header.put("RIFF".getBytes());
        header.putInt(36 + pcmBytes);
        header.put("WAVE".getBytes());
        header.put("fmt ".getBytes());
        header.putInt(16);
        header.putShort((short) 1);      // PCM
        header.putShort((short) 1);      // mono
        header.putInt(16000);            // sample rate
        header.putInt(byteRate);         // byte rate @ offset 28
        header.putShort((short) 2);      // block align
        header.putShort((short) 16);     // bits per sample
        header.put("data".getBytes());
        header.putInt(pcmBytes);
        out.writeBytes(header.array());
        out.writeBytes(new byte[pcmBytes]);
        return out.toByteArray();
    }

    @Test
    void parses_transcript_from_message_content() {
        server.expect(requestTo(URL))
                .andExpect(method(POST))
                .andRespond(withSuccess(
                        """
                        {"choices":[{"message":{"content":"今天天气不错，我想去公园跑步。","role":"assistant"}}]}
                        """, MediaType.APPLICATION_JSON));

        AsrResult r = provider.transcribe(wav(3200), "wav");

        assertThat(r.getText()).isEqualTo("今天天气不错，我想去公园跑步。");
        assertThat(r.getProvider()).isEqualTo("mimo");
    }

    /**
     * 锁死网关约束：content 数组必须恰好一个 input_audio，且不得含 text part。
     * 违反任一条上游都返回 400 —— 这个断言就是为了防止后来者"顺手加个提示词"。
     */
    @Test
    void request_body_carries_exactly_one_input_audio_and_no_text_part() {
        server.expect(requestTo(URL))
                .andExpect(method(POST))
                .andExpect(jsonPath("$.model").value("mimo-v2.5-asr"))
                .andExpect(jsonPath("$.messages.length()").value(1))
                .andExpect(jsonPath("$.messages[0].role").value("user"))
                .andExpect(jsonPath("$.messages[0].content.length()").value(1))
                .andExpect(jsonPath("$.messages[0].content[0].type").value("input_audio"))
                .andExpect(jsonPath("$.messages[0].content[0].input_audio.format").value("wav"))
                .andExpect(jsonPath("$.messages[0].content[0].input_audio.data").exists())
                .andRespond(withSuccess("""
                        {"choices":[{"message":{"content":"ok"}}]}
                        """, MediaType.APPLICATION_JSON));

        provider.transcribe(wav(3200), "wav");
        server.verify();
    }

    /** 静音/噪声：网关返回空 content，属正常结果，不应抛异常。 */
    @Test
    void empty_content_yields_empty_text_not_error() {
        server.expect(requestTo(URL))
                .andRespond(withSuccess("""
                        {"choices":[{"message":{"content":""}}]}
                        """, MediaType.APPLICATION_JSON));

        assertThat(provider.transcribe(wav(3200), "wav").getText()).isEmpty();
    }

    /** MediaRecorder 的原生输出格式必须在进网关前就被拒，避免浪费一次上传。 */
    @Test
    void rejects_formats_outside_gateway_whitelist() {
        assertThatThrownBy(() -> provider.transcribe(wav(3200), "webm"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("仅 wav / mp3");
        assertThatThrownBy(() -> provider.transcribe(wav(3200), "mp4"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void rejects_empty_audio() {
        assertThatThrownBy(() -> provider.transcribe(new byte[0], "wav"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("音频内容为空");
    }

    @Test
    void maps_upstream_failure_to_503() {
        server.expect(requestTo(URL)).andRespond(withServerError());

        assertThatThrownBy(() -> provider.transcribe(wav(3200), "wav"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("unavailable");
    }

    /** 时长从 WAV 头 ByteRate 推算：32000 字节 / 32000 每秒 = 1.0s。 */
    @Test
    void estimates_duration_from_wav_header() {
        server.expect(requestTo(URL))
                .andRespond(withSuccess("""
                        {"choices":[{"message":{"content":"x"}}]}
                        """, MediaType.APPLICATION_JSON));

        assertThat(provider.transcribe(wav(32000), "wav").getDurationSec()).isEqualTo(1.0);
    }
}
