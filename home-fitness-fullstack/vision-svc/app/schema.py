"""与 Java RoomFeatures 镜像的 Pydantic 模型。"""
from __future__ import annotations

from typing import List, Optional

from pydantic import BaseModel, Field


class Obstacle(BaseModel):
    label: str
    bbox: List[int] = Field(..., min_length=4, max_length=4)
    distanceM: Optional[float] = None
    side: Optional[str] = None


class DiscouragedAction(BaseModel):
    action: str
    reason: str


class RoomFeatures(BaseModel):
    areaSqm: Optional[float] = None
    areaConfidence: Optional[float] = None
    roomType: str = "unknown"
    lighting: str = "good"
    floor: str = "unknown"
    obstacles: List[Obstacle] = []
    recommendedActions: List[str] = []
    discouragedActions: List[DiscouragedAction] = []
    safetyScore: Optional[int] = None
    warnings: List[str] = []
    model: str = "placeholder-v0"


class FormIssue(BaseModel):
    """单条动作问题。joint: 涉及部位（knee/back/hip…）；severity: minor|major。"""

    joint: Optional[str] = None
    severity: str = "minor"
    detail: str


class FormCritique(BaseModel):
    """动作视觉点评 — JoyAI-VL 看训练关键帧给出的自然语言反馈。"""

    action: str
    summary: str = ""
    issues: List[FormIssue] = []
    tips: List[str] = []
    formScore: Optional[int] = None
    model: str = "placeholder-v0"
