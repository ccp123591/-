"""房间环境推理入口。

analyze_frames 是调度器：
- 配置了 JoyAI-VL 端点（JOYAI_VL_BASE_URL）时，优先走真实 VL 推理；失败则回落占位实现。
- 未配置端点时，直接走占位实现（图片字节 hash 衍生稳定伪随机特征），保证 e2e 链路通。

两条路径共用 rules 的动作/安全规则，输出口径一致。
"""
from __future__ import annotations

import hashlib
import logging
from typing import List

from .config import JoyaiConfig, load_joyai_config
from .joyai_vl import JoyaiError, analyze_frames_joyai
from .rules import action_rules, safety_score
from .schema import Obstacle, RoomFeatures

logger = logging.getLogger("vision-svc")

_ROOM_TYPES = ["living-room", "bedroom", "office", "unknown"]
_LIGHTING = ["good", "good", "dim", "poor"]
_FLOOR = ["hardwood", "carpet", "tile", "unknown"]
_OBSTACLES = ["sofa", "chair", "table", "bed", "tv", "wall"]
_SIDES = ["left", "right", "front", "behind"]

# 进程级缓存一次配置（环境变量在启动后不变）
_CONFIG: JoyaiConfig = load_joyai_config()


def analyze_frames(payloads: List[bytes]) -> RoomFeatures:
    """根据配置选择 JoyAI-VL 或占位推理；JoyAI-VL 失败时优雅回落。"""
    if not payloads:
        return RoomFeatures()

    if _CONFIG.enabled:
        try:
            return analyze_frames_joyai(payloads, _CONFIG)
        except JoyaiError as e:
            logger.warning("joyai-vl 推理失败，回落占位实现: %s", e)

    return _analyze_placeholder(payloads)


def _hash_seq(payloads: List[bytes]) -> bytes:
    h = hashlib.sha256()
    for p in payloads:
        h.update(p)
    return h.digest()


def _analyze_placeholder(payloads: List[bytes]) -> RoomFeatures:
    """占位推理 — 用图片字节 hash 衍生稳定的伪随机特征。"""
    digest = _hash_seq(payloads)
    seed = int.from_bytes(digest[:4], "big")

    # 面积 4 ~ 16 ㎡（合理客厅范围），confidence 0.5 ~ 0.85
    area = 4.0 + (seed % 1200) / 100.0
    confidence = 0.5 + (digest[5] & 0x7F) / 256.0

    room_type = _ROOM_TYPES[digest[6] % len(_ROOM_TYPES)]
    lighting = _LIGHTING[digest[7] % len(_LIGHTING)]
    floor = _FLOOR[digest[8] % len(_FLOOR)]

    # 1 ~ 2 个障碍物
    obstacle_count = 1 + (digest[9] & 1)
    obstacles: List[Obstacle] = []
    for i in range(obstacle_count):
        b = digest[10 + i * 4 : 14 + i * 4]
        label = _OBSTACLES[b[0] % len(_OBSTACLES)]
        side = _SIDES[b[1] % len(_SIDES)]
        distance = round(0.6 + (b[2] & 0x3F) / 30.0, 1)  # 0.6 ~ 2.7 m
        x1 = 50 + (b[3] & 0x3F) * 8
        bbox = [x1, 200, x1 + 320, 520]
        obstacles.append(Obstacle(label=label, bbox=bbox, distanceM=distance, side=side))

    recommended, discouraged, warnings = action_rules(area, lighting, obstacles)
    score = safety_score(area, lighting, obstacles)

    return RoomFeatures(
        areaSqm=round(area, 2),
        areaConfidence=round(confidence, 2),
        roomType=room_type,
        lighting=lighting,
        floor=floor,
        obstacles=obstacles,
        recommendedActions=recommended,
        discouragedActions=discouraged,
        safetyScore=score,
        warnings=warnings,
        model="placeholder-v0",
    )
