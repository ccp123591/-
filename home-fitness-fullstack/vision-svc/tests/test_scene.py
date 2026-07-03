import json

from fastapi.testclient import TestClient

from app import joyai_vl
from app import scene as scene_mod
from app.config import JoyaiConfig
from app.joyai_vl import _to_scene_summary, describe_scene_joyai
from app.main import app

client = TestClient(app)

_SAMPLE = {
    "summary": "用户正坐在瑜伽垫上压腿，客厅光线充足。",
    "personPresent": True,
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


def test_to_scene_summary_maps_fields():
    s = _to_scene_summary(_SAMPLE)
    assert s.model == "joyai-vl"
    assert s.personPresent is True
    assert "瑜伽垫" in s.summary


def test_to_scene_summary_tolerates_bad_fields():
    s = _to_scene_summary({"summary": 42, "personPresent": "yes"})
    assert s.summary == ""
    assert s.personPresent is None


def test_describe_scene_joyai_success(monkeypatch):
    monkeypatch.setattr(joyai_vl.httpx, "post",
                        lambda *a, **k: _FakeResp(json.dumps(_SAMPLE)))
    s = describe_scene_joyai([b"\xff\xd8\xff\xe0fake"], _cfg())
    assert s.model == "joyai-vl"
    assert s.personPresent is True


def test_dispatcher_falls_back_when_disabled(monkeypatch):
    # 未配端点（base_url 空）→ 空摘要，调用方跳过注入
    monkeypatch.setattr(scene_mod, "_CONFIG",
                        JoyaiConfig("", "joyai-vl", "", 5.0, 512))
    s = scene_mod.describe_scene([b"x"])
    assert s.model == "placeholder-v0"
    assert s.summary == ""


def test_dispatcher_falls_back_on_joyai_failure(monkeypatch):
    monkeypatch.setattr(scene_mod, "_CONFIG", _cfg())

    def _boom(payloads, config):
        raise joyai_vl.JoyaiError("down")

    monkeypatch.setattr(scene_mod, "describe_scene_joyai", _boom)
    s = scene_mod.describe_scene([b"x"])
    assert s.model == "placeholder-v0"
    assert s.summary == ""


def test_scene_endpoint_default_fallback():
    files = [("frames", ("a.jpg", b"\xff\xd8\xff\xe0fake", "image/jpeg"))]
    r = client.post("/scene", files=files)
    assert r.status_code == 200
    body = r.json()
    assert body["model"] == "placeholder-v0"  # 测试环境未配 JoyAI-VL
    assert body["summary"] == ""


def test_scene_endpoint_rejects_non_image():
    files = [("frames", ("a.txt", b"hi", "text/plain"))]
    r = client.post("/scene", files=files)
    assert r.status_code == 400
