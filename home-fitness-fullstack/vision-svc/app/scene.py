"""畅聊场景摘要调度器。

配置了 JoyAI-VL 端点时看抓帧出场景摘要；否则返回空 summary + placeholder 模型——
调用方（backend 畅聊）看到空 summary 就跳过注入，绝不编造"看到了什么"。
"""
from __future__ import annotations

import logging
from typing import List

from .config import JoyaiConfig, load_joyai_config
from .joyai_vl import JoyaiError, describe_scene_joyai
from .schema import SceneSummary

logger = logging.getLogger("vision-svc")

_CONFIG: JoyaiConfig = load_joyai_config()


def describe_scene(payloads: List[bytes]) -> SceneSummary:
    """根据配置选择 JoyAI-VL 场景摘要或空兜底；JoyAI-VL 失败时优雅回落。"""
    if _CONFIG.enabled and payloads:
        try:
            return describe_scene_joyai(payloads, _CONFIG)
        except JoyaiError as e:
            logger.warning("joyai-vl 场景摘要失败，返回空摘要: %s", e)

    return SceneSummary(summary="", personPresent=None, model="placeholder-v0")
