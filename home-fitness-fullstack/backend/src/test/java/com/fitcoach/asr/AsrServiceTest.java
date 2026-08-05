package com.fitcoach.asr;

import com.fitcoach.exception.BusinessException;
import com.fitcoach.infra.asr.AsrResult;
import com.fitcoach.infra.asr.MimoAsrProvider;
import com.fitcoach.infra.storage.UploadValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AsrServiceTest {

    @Mock MimoAsrProvider mimo;
    @Mock UploadValidator uploadValidator;

    @InjectMocks AsrService service;

    private static MockMultipartFile audio(int bytes) {
        return new MockMultipartFile("file", "speech.wav", "audio/wav", new byte[bytes]);
    }

    @BeforeEach
    void setup() {
        ReflectionTestUtils.setField(service, "configured", "mimo");
        given(mimo.isAvailable()).willReturn(true);
        given(mimo.name()).willReturn("mimo");
        given(uploadValidator.validateAudio(any())).willReturn("wav");
        given(mimo.transcribe(any(), any())).willReturn(
                AsrResult.builder().text("你好").provider("mimo").durationSec(1.0).build());
    }

    @Test
    void enabled_only_when_provider_selected_and_key_present() {
        assertThat(service.isEnabled()).isTrue();

        ReflectionTestUtils.setField(service, "configured", "none");
        assertThat(service.isEnabled()).isFalse();

        ReflectionTestUtils.setField(service, "configured", "mimo");
        given(mimo.isAvailable()).willReturn(false);
        assertThat(service.isEnabled()).isFalse();
    }

    @Test
    void transcribes_when_enabled() {
        assertThat(service.transcribe(audio(8192)).getText()).isEqualTo("你好");
    }

    /** 未启用时必须抛 503 而不是静默返回空 —— 前端据此切换到文字输入。 */
    @Test
    void throws_503_when_disabled() {
        ReflectionTestUtils.setField(service, "configured", "none");

        assertThatThrownBy(() -> service.transcribe(audio(8192)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("未启用");
    }

    /** 过短音频当作静音处理，不应发起云端调用。 */
    @Test
    void short_audio_returns_empty_without_calling_provider() {
        AsrResult r = service.transcribe(audio(512));

        assertThat(r.getText()).isEmpty();
        verify(mimo, never()).transcribe(any(), any());
    }
}
