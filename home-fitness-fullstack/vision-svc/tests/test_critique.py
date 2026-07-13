import json

from fastapi.testclient import TestClient

from app import critique as critique_mod
from app import joyai_vl
from app.config import JoyaiConfig
from app.joyai_vl import _to_form_critique, critique_frames_joyai
from app.main import app

client = TestClient(app)

_SAMPLE = {
    "summary": "整体不错，但下蹲深度不够。",
    "issues": [
        {"joint": "knee", "severity": "major", "detail": "膝盖轻微内扣"},
        {"joint": "hip", "severity": "minor", "detail": "下蹲深度偏浅"},
    ],
    "tips": ["膝盖对准脚尖", "再蹲低一点"],
    "formScore": 78,
}


def _cfg() -> JoyaiConfig:
    return JoyaiConfig(base_url="http://joyai-vl:8000/v1", model="joyai-vl",
                       api_key="", timeout_seconds=5.0, max_tokens=512)


class _FakeResp:
    def __init__(self, content: str):
        self._content = content

    def raise_for_status(self):
        return None

    def json(self):
        return {"choices": [{"message": {"content": self._content}}]}


def test_to_form_critique_maps_fields():
    fc = _to_form_critique("squat", _SAMPLE)
    assert fc.model == "joyai-vl"
    assert fc.action == "squat"
    assert fc.formScore == 78
    assert len(fc.issues) == 2
    assert fc.issues[0].severity == "major"
    assert fc.tips == ["膝盖对准脚尖", "再蹲低一点"]


def test_to_form_critique_clamps_and_filters():
    fc = _to_form_critique("squat", {"formScore": 250, "issues": [{"detail": ""}, "bad"],
                                     "tips": ["ok", 1, ""]})
    assert fc.formScore == 100
    assert fc.issues == []          # 空 detail / 非 dict 被过滤
    assert fc.tips == ["ok"]        # 非字符串 / 空串被过滤


def test_critique_frames_joyai_success(monkeypatch):
    monkeypatch.setattr(joyai_vl.httpx, "post",
                        lambda *a, **k: _FakeResp(json.dumps(_SAMPLE)))
    fc = critique_frames_joyai([b"\xff\xd8\xff\xe0fake"], "squat", {"reps": 12}, _cfg())
    assert fc.model == "joyai-vl"
    assert fc.formScore == 78


def test_dispatcher_falls_back_when_disabled(monkeypatch):
    # 未配端点（base_url 空）→ 通用兜底
    monkeypatch.setattr(critique_mod, "_CONFIG",
                        JoyaiConfig("", "joyai-vl", "", 5.0, 512))
    fc = critique_mod.critique_frames([b"x"], "squat")
    assert fc.model == "placeholder-v0"
    assert len(fc.tips) >= 1


def test_dispatcher_falls_back_on_joyai_failure(monkeypatch):
    monkeypatch.setattr(critique_mod, "_CONFIG", _cfg())

    def _boom(payloads, action, metrics, config):
        raise joyai_vl.JoyaiError("down")

    monkeypatch.setattr(critique_mod, "critique_frames_joyai", _boom)
    fc = critique_mod.critique_frames([b"x"], "pushup")
    assert fc.model == "placeholder-v0"
    assert fc.action == "pushup"


def test_critique_endpoint_default_fallback():
    files = [("frames", ("a.jpg", b"\xff\xd8\xff\xe0fake", "image/jpeg"))]
    r = client.post("/critique", data={"action": "squat", "reps": 10}, files=files)
    assert r.status_code == 200
    body = r.json()
    assert body["action"] == "squat"
    assert body["model"] == "placeholder-v0"  # 测试环境未配 JoyAI-VL


def test_critique_endpoint_requires_action():
    files = [("frames", ("a.jpg", b"x", "image/jpeg"))]
    r = client.post("/critique", data={"action": "  "}, files=files)
    assert r.status_code == 400


def test_critique_endpoint_rejects_non_image():
    files = [("frames", ("a.txt", b"hi", "text/plain"))]
    r = client.post("/critique", data={"action": "squat"}, files=files)
    assert r.status_code == 400
