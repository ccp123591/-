package com.fitcoach.room;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitcoach.exception.BusinessException;
import com.fitcoach.infra.vision.RoomFeatures;
import com.fitcoach.infra.vision.VisionClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RoomLayoutServiceTest {

    @Mock VisionClient visionClient;
    @Mock RoomLayoutRepository repo;

    @InjectMocks RoomLayoutService service;

    private MultipartFile fakeJpg() {
        return new MockMultipartFile("frames", "front.jpg", "image/jpeg", new byte[]{1, 2, 3});
    }

    private RoomFeatures sampleFeatures() {
        return RoomFeatures.builder()
                .areaSqm(6.2).areaConfidence(0.7)
                .roomType("living-room")
                .lighting("good").floor("hardwood")
                .obstacles(List.of(RoomFeatures.Obstacle.builder()
                        .label("sofa").bbox(new int[]{120, 260, 540, 560})
                        .distanceM(1.2).side("left").build()))
                .recommendedActions(List.of("squat", "stretch", "bridge", "plank"))
                .discouragedActions(List.of(RoomFeatures.DiscouragedAction.builder()
                        .action("jumpingJack").reason("空间过小").build()))
                .safetyScore(78)
                .warnings(List.of("左侧 1.2m 处沙发，跳跃动作不安全"))
                .model("joyai-vl")
                .build();
    }

    @Test
    void scan_calls_vision_and_persists_with_summary() {
        given(visionClient.infer(any())).willReturn(sampleFeatures());
        given(repo.save(any())).willAnswer(inv -> inv.getArgument(0));

        var resp = service.scan(7L, List.of(fakeJpg()));

        assertThat(resp.getAreaSqm()).isEqualByComparingTo(new BigDecimal("6.20"));
        assertThat(resp.getSafetyScore()).isEqualTo(78);
        assertThat(resp.getSummaryText()).contains("客厅").contains("6.2").contains("沙发");
        verify(repo).save(any(RoomLayoutSnapshot.class));
    }

    @Test
    void scan_rejects_zero_frames() {
        assertThatThrownBy(() -> service.scan(7L, List.of()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("至少");
        verifyNoInteractions(visionClient);
    }

    @Test
    void scan_rejects_more_than_3_frames() {
        assertThatThrownBy(() -> service.scan(7L,
                List.of(fakeJpg(), fakeJpg(), fakeJpg(), fakeJpg())))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("最多");
    }

    @Test
    void scan_rejects_placeholder_safety_results() {
        RoomFeatures placeholder = sampleFeatures();
        placeholder.setModel("placeholder-v0");
        given(visionClient.infer(any())).willReturn(placeholder);

        assertThatThrownBy(() -> service.scan(7L, List.of(fakeJpg())))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无法生成可信");
        verifyNoInteractions(repo);
    }

    @Test
    void latest_returns_404_when_no_scan() {
        given(repo.findTopByUserIdOrderByCapturedAtDesc(7L)).willReturn(Optional.empty());
        assertThatThrownBy(() -> service.latest(7L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("尚未");
    }

    @Test
    void override_area_clamps_and_updates_summary() {
        ObjectMapper mapper = new ObjectMapper();
        RoomFeatures f = sampleFeatures();
        f.setAreaSqm(6.0);
        f.setAreaConfidence(0.5);
        String json;
        try { json = mapper.writeValueAsString(f); } catch (Exception e) { throw new RuntimeException(e); }
        RoomLayoutSnapshot snap = RoomLayoutSnapshot.builder()
                .id(1L).userId(7L).scanId("uuid").featuresJson(json)
                .summaryText("old").areaSqm(new BigDecimal("6.00")).build();
        given(repo.findTopByUserIdOrderByCapturedAtDesc(7L)).willReturn(Optional.of(snap));
        given(repo.save(any())).willAnswer(inv -> inv.getArgument(0));

        var resp = service.overrideArea(7L, new BigDecimal("8.50"));

        assertThat(resp.getAreaSqm()).isEqualByComparingTo(new BigDecimal("8.50"));
        assertThat(resp.getSummaryText()).contains("8.5");
    }

    @Test
    void override_area_rejects_out_of_range() {
        assertThatThrownBy(() -> service.overrideArea(7L, new BigDecimal("0")))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.overrideArea(7L, new BigDecimal("501")))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void buildSummary_handles_missing_fields() {
        RoomFeatures sparse = RoomFeatures.builder().roomType("unknown").build();
        String s = RoomLayoutService.buildSummary(sparse);
        assertThat(s).isNotBlank().contains("训练空间");
    }
}
