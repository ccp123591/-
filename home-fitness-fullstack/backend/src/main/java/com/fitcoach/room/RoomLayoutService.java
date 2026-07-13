package com.fitcoach.room;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitcoach.common.PageResult;
import com.fitcoach.exception.BusinessException;
import com.fitcoach.infra.vision.RoomFeatures;
import com.fitcoach.infra.vision.VisionClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 训练空间评估业务：上传 → 调 vision-svc → 抽象入库 + 生成中文 summary → 返回。
 * 原图不持久化（vision-svc 处理完就丢；后端只看到字节流）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RoomLayoutService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final VisionClient visionClient;
    private final RoomLayoutRepository repo;

    @Value("${vision.allow-placeholder-results:false}")
    private boolean allowPlaceholderResults;

    @Transactional
    public RoomLayoutResponse scan(Long userId, List<MultipartFile> frames) {
        if (frames == null || frames.isEmpty()) {
            throw new BusinessException(400, "请至少上传 1 帧");
        }
        if (frames.size() > 3) {
            throw new BusinessException(400, "最多上传 3 帧");
        }

        LocalDateTime captured = LocalDateTime.now();
        RoomFeatures features = visionClient.infer(frames);
        String model = features == null ? null : features.getModel();
        if (!allowPlaceholderResults && (model == null || model.startsWith("placeholder"))) {
            throw new BusinessException(503, "JoyAI 视觉服务未配置，无法生成可信的环境安全评估");
        }
        LocalDateTime processed = LocalDateTime.now();

        String json;
        try {
            json = MAPPER.writeValueAsString(features);
        } catch (JsonProcessingException e) {
            throw new BusinessException(500, "推理结果序列化失败");
        }

        String summary = buildSummary(features);
        RoomLayoutSnapshot snap = RoomLayoutSnapshot.builder()
                .userId(userId)
                .scanId(UUID.randomUUID().toString())
                .featuresJson(json)
                .summaryText(summary)
                .areaSqm(features.getAreaSqm() == null ? null
                        : BigDecimal.valueOf(features.getAreaSqm()).setScale(2, RoundingMode.HALF_UP))
                .safetyScore(features.getSafetyScore())
                .source("camera")
                .visionModel(features.getModel() == null ? "unknown" : features.getModel())
                .capturedAt(captured)
                .processedAt(processed)
                .build();
        repo.save(snap);
        log.info("[room] user={} scanId={} area={}㎡ safety={} model={}",
                userId, snap.getScanId(), features.getAreaSqm(), features.getSafetyScore(), snap.getVisionModel());

        return toResponse(snap, features);
    }

    @Transactional(readOnly = true)
    public RoomLayoutResponse latest(Long userId) {
        RoomLayoutSnapshot snap = repo.findTopByUserIdOrderByCapturedAtDesc(userId)
                .orElseThrow(() -> new BusinessException(404, "尚未进行过环境扫描"));
        return toResponse(snap, deserialize(snap.getFeaturesJson()));
    }

    @Transactional(readOnly = true)
    public PageResult<RoomLayoutResponse> history(Long userId, int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(size, 50));
        Page<RoomLayoutSnapshot> p = repo.findByUserIdOrderByCapturedAtDesc(userId,
                PageRequest.of(safePage, safeSize));
        List<RoomLayoutResponse> items = p.getContent().stream()
                .map(s -> toResponse(s, deserialize(s.getFeaturesJson()))).toList();
        return PageResult.of(items, p.getTotalElements(), safePage, safeSize);
    }

    /** 手动覆盖最新一次扫描的米制面积 — 老人/家属比 CV 更准。 */
    @Transactional
    public RoomLayoutResponse overrideArea(Long userId, BigDecimal areaSqm) {
        if (areaSqm == null || areaSqm.signum() <= 0 || areaSqm.compareTo(BigDecimal.valueOf(500)) > 0) {
            throw new BusinessException(400, "面积应在 (0, 500] ㎡ 范围");
        }
        RoomLayoutSnapshot snap = repo.findTopByUserIdOrderByCapturedAtDesc(userId)
                .orElseThrow(() -> new BusinessException(404, "请先完成一次环境扫描"));
        snap.setAreaSqm(areaSqm.setScale(2, RoundingMode.HALF_UP));
        // 更新 features_json 里的 areaSqm 与 areaConfidence=1.0（手动覆盖即满信心）
        RoomFeatures f = deserialize(snap.getFeaturesJson());
        f.setAreaSqm(areaSqm.doubleValue());
        f.setAreaConfidence(1.0);
        try {
            snap.setFeaturesJson(MAPPER.writeValueAsString(f));
        } catch (JsonProcessingException e) {
            throw new BusinessException(500, "更新失败");
        }
        snap.setSummaryText(buildSummary(f));
        repo.save(snap);
        return toResponse(snap, f);
    }

    /** 给 coach 用的最新 summary（缺失时返回 null）。 */
    @Transactional(readOnly = true)
    public String latestSummaryForCoach(Long userId) {
        return repo.findTopByUserIdOrderByCapturedAtDesc(userId)
                .map(RoomLayoutSnapshot::getSummaryText).orElse(null);
    }

    // ---- helpers ----

    private RoomLayoutResponse toResponse(RoomLayoutSnapshot s, RoomFeatures f) {
        return RoomLayoutResponse.builder()
                .id(s.getId())
                .scanId(s.getScanId())
                .summaryText(s.getSummaryText())
                .areaSqm(s.getAreaSqm())
                .safetyScore(s.getSafetyScore())
                .visionModel(s.getVisionModel())
                .capturedAt(s.getCapturedAt())
                .processedAt(s.getProcessedAt())
                .features(f)
                .build();
    }

    private RoomFeatures deserialize(String json) {
        try {
            return MAPPER.readValue(json, RoomFeatures.class);
        } catch (Exception e) {
            return null;
        }
    }

    /** 拼装中文一句话 summary。 */
    static String buildSummary(RoomFeatures f) {
        StringBuilder sb = new StringBuilder();
        String roomZh = switch (f.getRoomType() == null ? "" : f.getRoomType()) {
            case "living-room" -> "客厅";
            case "bedroom" -> "卧室";
            case "office" -> "书房";
            default -> "训练空间";
        };
        sb.append(roomZh);
        if (f.getAreaSqm() != null) {
            sb.append("约 ").append(BigDecimal.valueOf(f.getAreaSqm())
                    .setScale(1, RoundingMode.HALF_UP).toPlainString()).append("㎡");
        }
        String lightZh = switch (f.getLighting() == null ? "" : f.getLighting()) {
            case "good" -> "光线良好";
            case "dim" -> "光线偏暗";
            case "poor" -> "光线很差";
            default -> null;
        };
        if (lightZh != null) sb.append("，").append(lightZh);
        if (f.getObstacles() != null && !f.getObstacles().isEmpty()) {
            var o = f.getObstacles().get(0);
            String sideZh = switch (o.getSide() == null ? "" : o.getSide()) {
                case "left" -> "左侧";
                case "right" -> "右侧";
                case "front" -> "正前方";
                case "behind" -> "正后方";
                default -> "附近";
            };
            sb.append("，").append(sideZh);
            if (o.getDistanceM() != null) {
                sb.append(" ").append(BigDecimal.valueOf(o.getDistanceM())
                        .setScale(1, RoundingMode.HALF_UP).toPlainString()).append("m");
            }
            sb.append(" 有 ").append(zhObstacle(o.getLabel()));
            if (f.getObstacles().size() > 1) sb.append(" 等 ").append(f.getObstacles().size()).append(" 处障碍物");
        }
        if (f.getRecommendedActions() != null && !f.getRecommendedActions().isEmpty()) {
            sb.append("；推荐 ").append(String.join("/", f.getRecommendedActions().stream().limit(4).toList()));
        }
        return sb.toString();
    }

    private static String zhObstacle(String label) {
        if (label == null) return "障碍物";
        return switch (label) {
            case "sofa" -> "沙发";
            case "chair" -> "椅子";
            case "table", "coffee-table" -> "桌子";
            case "bed" -> "床";
            case "tv" -> "电视";
            case "wall" -> "墙";
            case "door" -> "门";
            case "carpet" -> "地毯";
            default -> label;
        };
    }
}
