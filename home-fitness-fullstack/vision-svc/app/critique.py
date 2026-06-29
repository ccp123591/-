"""动作视觉点评调度器。

配置了 JoyAI-VL 端点时走真实视觉点评（看关键帧）；否则回落到按动作类型的
通用提示——并在 summary 里诚实说明"视觉点评未启用"，避免把模板话术伪装成真分析。
"""
from __future__ import annotations

import logging
from typing import Any, Dict, List, Optional

from .config import JoyaiConfig, load_joyai_config
from .joyai_vl import JoyaiError, critique_frames_joyai
from .schema import FormCritique, FormIssue

logger = logging.getLogger("vision-svc")

_CONFIG: JoyaiConfig = load_joyai_config()

# 端点未启用时的兜底提示（不依赖画面，仅按动作类型给通用要点）
_GENERIC_TIPS: Dict[str, List[str]] = {
    "squat": ["膝盖与脚尖同向，避免内扣", "下蹲时重心压在脚跟", "髋部先后坐再下蹲"],
    "stretch": ["呼气时缓慢加深，不要弹振", "保持背部延展而非弓背"],
    "pushup": ["身体绷成一条直线，别塌腰翘臀", "下压时肘部约 45° 夹角"],
    "lunge": ["前膝不超过脚尖，重心居中", "上身保持竖直，核心收紧"],
    "bridge": ["顶峰夹紧臀部停一拍", "用臀发力而非腰部代偿"],
    "plank": ["肩-髋-踝绷成一条线", "收紧核心，别塌腰也别拱背"],
    "jumpingJack": ["手臂举过头顶做满幅度", "落地屈膝缓冲，保护膝踝"],
}


def critique_frames(
    payloads: List[bytes],
    action: str,
    metrics: Optional[Dict[str, Any]] = None,
) -> FormCritique:
    """根据配置选择 JoyAI-VL 视觉点评或通用兜底；JoyAI-VL 失败时优雅回落。"""
    if _CONFIG.enabled and payloads:
        try:
            return critique_frames_joyai(payloads, action, metrics, _CONFIG)
        except JoyaiError as e:
            logger.warning("joyai-vl 点评失败，回落通用提示: %s", e)

    return _fallback_critique(action)


def _fallback_critique(action: str) -> FormCritique:
    tips = _GENERIC_TIPS.get(action, ["保持动作稳定、幅度做满，量力而行"])
    return FormCritique(
        action=action,
        summary="（视觉点评未启用，以下为该动作的通用要点）",
        issues=[FormIssue(joint=None, severity="minor", detail="未接入视觉模型，无法针对本次画面点评")],
        tips=tips,
        formScore=None,
        model="placeholder-v0",
    )
