"""环境变量驱动的服务配置（不可变）。

JoyAI-VL 端点未配置（JOYAI_VL_BASE_URL 为空）时，推理自动回落到占位实现，
保证 e2e 链路在没有 GPU / 没有模型服务时依然能跑通。
"""
from __future__ import annotations

import os
from dataclasses import dataclass


@dataclass(frozen=True)
class JoyaiConfig:
    """JoyAI-VL（OpenAI 兼容）端点配置。base_url 为空即视为未启用。"""

    base_url: str
    model: str
    api_key: str
    timeout_seconds: float
    max_tokens: int

    @property
    def enabled(self) -> bool:
        return bool(self.base_url)


def load_joyai_config() -> JoyaiConfig:
    """从环境变量加载 JoyAI-VL 配置。"""
    return JoyaiConfig(
        base_url=os.environ.get("JOYAI_VL_BASE_URL", "").strip().rstrip("/"),
        model=os.environ.get("JOYAI_VL_MODEL", "joyai-vl").strip(),
        api_key=os.environ.get("JOYAI_VL_API_KEY", "").strip(),
        timeout_seconds=float(os.environ.get("JOYAI_VL_TIMEOUT", "25")),
        max_tokens=int(os.environ.get("JOYAI_VL_MAX_TOKENS", "1024")),
    )
