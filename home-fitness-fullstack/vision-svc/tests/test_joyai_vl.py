import json

import pytest

from app import inference, joyai_vl
from app.config import JoyaiConfig
from app.joyai_vl import (
    JoyaiError,
    _extract_json,
    _to_room_features,
    analyze_frames_joyai,
)

_SAMPLE = {
    "areaSqm": 12.5,
    "areaConfidence": 0.8,
    "roomType": "living-room",
    "lighting": "good",
    "floor": "hardwood",
    "obstacles": [
        {"label": "sofa", "bbox": [10, 20, 300, 400], "distanceM": 1.6, "side": "left"},
        {"label": "table", "bbox": [400, 200, 600, 500], "distanceM": 0.7, "side": "front"},
    ],
}


def _cfg() -> JoyaiConfig:
    return JoyaiConfig(
        base_url="http://joyai-vl:8000/v1",
        model="joyai-vl",
        api_key="",
        timeout_seconds=5.0,
        max_tokens=512,
    )


class _FakeResp:
    def __init__(self, content: str):
        self._content = content

    def raise_for_status(self):
        return None

    def json(self):
        return {"choices": [{"message": {"content": self._content}}]}


def test_to_room_features_maps_and_applies_rules():
    rf = _to_room_features(_SAMPLE)
    assert rf.model == "joyai-vl"
    assert rf.areaSqm == 12.5
    assert rf.areaConfidence == 0.8
    assert rf.roomType == "living-room"
    assert len(rf.obstacles) == 2
    # 面积 >= 10 → jumpingJack 进推荐；但有障碍物 < 1m → lunge 被移除并告警
    assert "jumpingJack" in rf.recommendedActions
    assert "lunge" not in rf.recommendedActions
    assert any(d.action == "lunge" for d in rf.discouragedActions)
    assert rf.safetyScore is not None and 0 <= rf.safetyScore <= 100


def test_to_room_features_clamps_and_defaults_unknown_enums():
    rf = _to_room_features({"areaSqm": 999, "roomType": "garage", "lighting": "blinding"})
    assert rf.areaSqm == 60.0  # clamp 上限
    assert rf.roomType == "unknown"
    assert rf.lighting == "good"  # 非法枚举回默认
    assert rf.obstacles == []


def test_extract_json_handles_code_fence_and_prose():
    text = "Here you go:\n```json\n" + json.dumps(_SAMPLE) + "\n```\nDone."
    assert _extract_json(text)["roomType"] == "living-room"


def test_extract_json_raises_on_garbage():
    with pytest.raises(JoyaiError):
        _extract_json("no json here")


def test_analyze_frames_joyai_success(monkeypatch):
    monkeypatch.setattr(joyai_vl.httpx, "post",
                        lambda *a, **k: _FakeResp(json.dumps(_SAMPLE)))
    rf = analyze_frames_joyai([b"\xff\xd8\xff\xe0fake"], _cfg())
    assert rf.model == "joyai-vl"
    assert rf.roomType == "living-room"


def test_analyze_frames_joyai_raises_on_http_error(monkeypatch):
    def _boom(*a, **k):
        raise joyai_vl.httpx.ConnectError("refused")

    monkeypatch.setattr(joyai_vl.httpx, "post", _boom)
    with pytest.raises(JoyaiError):
        analyze_frames_joyai([b"\xff\xd8\xff\xe0fake"], _cfg())


def test_dispatcher_falls_back_to_placeholder_on_joyai_failure(monkeypatch):
    monkeypatch.setattr(inference, "_CONFIG", _cfg())

    def _boom(payloads, config):
        raise JoyaiError("down")

    monkeypatch.setattr(inference, "analyze_frames_joyai", _boom)
    rf = inference.analyze_frames([b"deterministic-bytes"])
    assert rf.model == "placeholder-v0"  # 优雅回落


def test_dispatcher_uses_joyai_when_enabled(monkeypatch):
    monkeypatch.setattr(inference, "_CONFIG", _cfg())
    monkeypatch.setattr(inference, "analyze_frames_joyai",
                        lambda payloads, config: _to_room_features(_SAMPLE))
    rf = inference.analyze_frames([b"x"])
    assert rf.model == "joyai-vl"
