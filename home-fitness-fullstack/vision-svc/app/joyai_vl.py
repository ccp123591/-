"""JoyAI-VL 推理路径：调用 OpenAI 兼容的视觉对话端点做真实房间分析。

JoyAI-VL（京东开源，Apache-2.0，基座 Qwen3-8B + Qwen3-VL ViT）由 vLLM 以
OpenAI 兼容协议提供服务。本模块把 1-3 帧房间图编码为 data-URL 多图消息，
用结构化 prompt 让模型只输出"看到的事实"（面积/光线/地面/障碍物），
随后统一交给 rules 推导动作清单与安全分——安全逻辑不交给模型自由发挥。

端点不可用 / 返回不可解析时抛 JoyaiError，由 inference.analyze_frames 回落到占位实现。
"""
from __future__ import annotations

import base64
import json
import logging
import re
from typing import Any, Dict, List, Optional

import httpx

from .config import JoyaiConfig
from .rules import action_rules, safety_score
from .schema import FormCritique, FormIssue, Obstacle, RoomFeatures, SceneSummary

logger = logging.getLogger("vision-svc")

_ROOM_TYPES = {"living-room", "bedroom", "office", "unknown"}
_LIGHTING = {"good", "dim", "poor"}
_FLOOR = {"hardwood", "carpet", "tile", "unknown"}
_SIDES = {"left", "right", "front", "behind"}

_SYSTEM_PROMPT = (
    "You are a home-fitness room analyzer. Look at the room photo(s) and report only what "
    "you actually observe. Estimate the clear floor area usable for exercise. Respond with a "
    "single JSON object and nothing else."
)

_USER_PROMPT = (
    "Analyze this room for safe home workouts and return strictly this JSON schema:\n"
    "{\n"
    '  "areaSqm": <number, clear floor area in square meters>,\n'
    '  "areaConfidence": <number 0-1>,\n'
    '  "roomType": "living-room" | "bedroom" | "office" | "unknown",\n'
    '  "lighting": "good" | "dim" | "poor",\n'
    '  "floor": "hardwood" | "carpet" | "tile" | "unknown",\n'
    '  "obstacles": [\n'
    '    {"label": <string>, "bbox": [x1,y1,x2,y2], "distanceM": <number>, '
    '"side": "left" | "right" | "front" | "behind"}\n'
    "  ]\n"
    "}\n"
    "Rules: report at most 5 obstacles, pixel bbox in the first image, distanceM is the "
    "estimated distance from the camera/person in meters. Output JSON only, no prose, no markdown."
)


class JoyaiError(RuntimeError):
    """JoyAI-VL 调用或解析失败——触发上层回落。"""


def _mime_type(payload: bytes) -> str:
    if payload[:3] == b"\xff\xd8\xff":
        return "image/jpeg"
    if payload[:8] == b"\x89PNG\r\n\x1a\n":
        return "image/png"
    if payload[:4] == b"RIFF" and payload[8:12] == b"WEBP":
        return "image/webp"
    return "image/jpeg"


def _data_url(payload: bytes) -> str:
    b64 = base64.b64encode(payload).decode("ascii")
    return f"data:{_mime_type(payload)};base64,{b64}"


def _build_messages(payloads: List[bytes]) -> List[Dict[str, Any]]:
    content: List[Dict[str, Any]] = [{"type": "text", "text": _USER_PROMPT}]
    for p in payloads:
        content.append({"type": "image_url", "image_url": {"url": _data_url(p)}})
    return [
        {"role": "system", "content": _SYSTEM_PROMPT},
        {"role": "user", "content": content},
    ]


def _extract_json(text: str) -> Dict[str, Any]:
    """从模型输出里抠出 JSON 对象（容忍 ```json 围栏和前后赘述）。"""
    stripped = text.strip()
    fence = re.search(r"```(?:json)?\s*(\{.*?\})\s*```", stripped, re.DOTALL)
    candidate = fence.group(1) if fence else None
    if candidate is None:
        start, end = stripped.find("{"), stripped.rfind("}")
        if start == -1 or end <= start:
            raise JoyaiError("响应中未找到 JSON 对象")
        candidate = stripped[start : end + 1]
    try:
        data = json.loads(candidate)
    except json.JSONDecodeError as e:
        raise JoyaiError(f"JSON 解析失败: {e}") from e
    if not isinstance(data, dict):
        raise JoyaiError("响应 JSON 不是对象")
    return data


def _enum(value: Any, allowed: set, default: str) -> str:
    return value if isinstance(value, str) and value in allowed else default


def _num(value: Any) -> Optional[float]:
    try:
        return float(value)
    except (TypeError, ValueError):
        return None


def _parse_obstacles(raw: Any) -> List[Obstacle]:
    obstacles: List[Obstacle] = []
    if not isinstance(raw, list):
        return obstacles
    for item in raw[:5]:
        if not isinstance(item, dict):
            continue
        label = item.get("label")
        if not isinstance(label, str) or not label:
            continue
        bbox_raw = item.get("bbox")
        if isinstance(bbox_raw, list) and len(bbox_raw) == 4:
            try:
                bbox = [int(round(float(v))) for v in bbox_raw]
            except (TypeError, ValueError):
                bbox = [0, 0, 0, 0]
        else:
            bbox = [0, 0, 0, 0]
        distance = _num(item.get("distanceM"))
        if distance is not None:
            distance = round(max(0.0, distance), 1)
        side = item.get("side") if item.get("side") in _SIDES else None
        obstacles.append(Obstacle(label=label, bbox=bbox, distanceM=distance, side=side))
    return obstacles


def _to_room_features(data: Dict[str, Any]) -> RoomFeatures:
    """把模型 JSON 映射成 RoomFeatures，动作/安全分一律走确定性规则。"""
    area = _num(data.get("areaSqm")) or 0.0
    area = round(max(0.0, min(area, 60.0)), 2)  # clamp 到合理范围
    confidence = _num(data.get("areaConfidence"))
    confidence = round(min(max(confidence, 0.0), 1.0), 2) if confidence is not None else None

    lighting = _enum(data.get("lighting"), _LIGHTING, "good")
    obstacles = _parse_obstacles(data.get("obstacles"))

    recommended, discouraged, warnings = action_rules(area, lighting, obstacles)
    score = safety_score(area, lighting, obstacles)

    return RoomFeatures(
        areaSqm=area,
        areaConfidence=confidence,
        roomType=_enum(data.get("roomType"), _ROOM_TYPES, "unknown"),
        lighting=lighting,
        floor=_enum(data.get("floor"), _FLOOR, "unknown"),
        obstacles=obstacles,
        recommendedActions=recommended,
        discouragedActions=discouraged,
        safetyScore=score,
        warnings=warnings,
        model="joyai-vl",
    )


def _request(config: JoyaiConfig, messages: List[Dict[str, Any]]) -> str:
    headers = {"Content-Type": "application/json"}
    if config.api_key:
        headers["Authorization"] = f"Bearer {config.api_key}"
    body = {
        "model": config.model,
        "messages": messages,
        "max_tokens": config.max_tokens,
        "temperature": 0.0,
    }
    try:
        resp = httpx.post(
            f"{config.base_url}/chat/completions",
            json=body,
            headers=headers,
            timeout=config.timeout_seconds,
        )
        resp.raise_for_status()
        payload = resp.json()
        return payload["choices"][0]["message"]["content"]
    except (httpx.HTTPError, KeyError, IndexError, ValueError) as e:
        raise JoyaiError(f"JoyAI-VL 请求失败: {e}") from e


def analyze_frames_joyai(payloads: List[bytes], config: JoyaiConfig) -> RoomFeatures:
    """调用 JoyAI-VL 做房间分析；任何环节失败抛 JoyaiError。"""
    if not payloads:
        raise JoyaiError("无输入帧")
    content = _request(config, _build_messages(payloads))
    features = _to_room_features(_extract_json(content))
    logger.info("joyai-vl inference ok: area=%.2f safety=%s obstacles=%d",
                features.areaSqm or 0.0, features.safetyScore, len(features.obstacles))
    return features


# —————————————————— 动作视觉点评 ——————————————————

_CRITIQUE_SYSTEM = (
    "You are a strict but encouraging home-fitness form coach. You are shown keyframes of a "
    "user performing one exercise. Judge their form from what you actually see and respond with "
    "a single JSON object and nothing else. Be specific about body parts and concrete fixes."
)

_CRITIQUE_SCHEMA = (
    "Return strictly this JSON schema:\n"
    "{\n"
    '  "summary": <one-sentence overall assessment, Chinese>,\n'
    '  "issues": [{"joint": <body part e.g. knee/back/hip>, '
    '"severity": "minor" | "major", "detail": <what is wrong, Chinese>}],\n'
    '  "tips": [<concrete fix, Chinese>],\n'
    '  "formScore": <integer 0-100>\n'
    "}\n"
    "Report at most 4 issues. Output JSON only, no prose, no markdown."
)


def _build_critique_messages(
    payloads: List[bytes], action: str, metrics: Optional[Dict[str, Any]]
) -> List[Dict[str, Any]]:
    ctx = f"Exercise: {action}."
    if metrics:
        bits = [f"{k}={v}" for k, v in metrics.items() if v is not None]
        if bits:
            ctx += " Measured this set: " + ", ".join(bits) + "."
    text = f"{ctx}\nReview the user's form across these keyframes.\n{_CRITIQUE_SCHEMA}"
    content: List[Dict[str, Any]] = [{"type": "text", "text": text}]
    for p in payloads:
        content.append({"type": "image_url", "image_url": {"url": _data_url(p)}})
    return [
        {"role": "system", "content": _CRITIQUE_SYSTEM},
        {"role": "user", "content": content},
    ]


def _parse_issues(raw: Any) -> List[FormIssue]:
    issues: List[FormIssue] = []
    if not isinstance(raw, list):
        return issues
    for item in raw[:4]:
        if not isinstance(item, dict):
            continue
        detail = item.get("detail")
        if not isinstance(detail, str) or not detail.strip():
            continue
        severity = item.get("severity")
        severity = severity if severity in ("minor", "major") else "minor"
        joint = item.get("joint") if isinstance(item.get("joint"), str) else None
        issues.append(FormIssue(joint=joint, severity=severity, detail=detail.strip()))
    return issues


def _to_form_critique(action: str, data: Dict[str, Any]) -> FormCritique:
    summary = data.get("summary")
    summary = summary.strip() if isinstance(summary, str) else ""
    tips = [t.strip() for t in data.get("tips", []) if isinstance(t, str) and t.strip()][:5]
    score = _num(data.get("formScore"))
    form_score = int(round(min(max(score, 0.0), 100.0))) if score is not None else None
    return FormCritique(
        action=action,
        summary=summary,
        issues=_parse_issues(data.get("issues")),
        tips=tips,
        formScore=form_score,
        model="joyai-vl",
    )


def critique_frames_joyai(
    payloads: List[bytes],
    action: str,
    metrics: Optional[Dict[str, Any]],
    config: JoyaiConfig,
) -> FormCritique:
    """调用 JoyAI-VL 做动作视觉点评；任何环节失败抛 JoyaiError。"""
    if not payloads:
        raise JoyaiError("无输入帧")
    content = _request(config, _build_critique_messages(payloads, action, metrics))
    critique = _to_form_critique(action, _extract_json(content))
    logger.info("joyai-vl critique ok: action=%s issues=%d score=%s",
                action, len(critique.issues), critique.formScore)
    return critique


# —————————————————— 畅聊场景摘要 ——————————————————

_SCENE_SYSTEM = (
    "You are the eyes of a home-fitness voice companion on a video call with the user. "
    "Look at the webcam frame(s) and briefly describe what you see so the companion can "
    "respond naturally. Respond with a single JSON object and nothing else."
)

_SCENE_PROMPT = (
    "Return strictly this JSON schema:\n"
    "{\n"
    '  "summary": <1-2 short sentences in Chinese: what the person is doing (posture, '
    "activity, holding anything) and any environment detail worth mentioning for home "
    "fitness (space, lighting, equipment). Empty string if the frame is unreadable>,\n"
    '  "personPresent": <true if a person is visible, else false>\n'
    "}\n"
    "Do not guess identity, age or emotions beyond the obvious. Output JSON only, "
    "no prose, no markdown."
)


def _to_scene_summary(data: Dict[str, Any]) -> SceneSummary:
    summary = data.get("summary")
    summary = summary.strip() if isinstance(summary, str) else ""
    present = data.get("personPresent")
    present = present if isinstance(present, bool) else None
    return SceneSummary(summary=summary, personPresent=present, model="joyai-vl")


def describe_scene_joyai(payloads: List[bytes], config: JoyaiConfig) -> SceneSummary:
    """调用 JoyAI-VL 做畅聊场景摘要；任何环节失败抛 JoyaiError。"""
    if not payloads:
        raise JoyaiError("无输入帧")
    content: List[Dict[str, Any]] = [{"type": "text", "text": _SCENE_PROMPT}]
    for p in payloads:
        content.append({"type": "image_url", "image_url": {"url": _data_url(p)}})
    messages = [
        {"role": "system", "content": _SCENE_SYSTEM},
        {"role": "user", "content": content},
    ]
    scene = _to_scene_summary(_extract_json(_request(config, messages)))
    logger.info("joyai-vl scene ok: person=%s summary_len=%d",
                scene.personPresent, len(scene.summary))
    return scene
