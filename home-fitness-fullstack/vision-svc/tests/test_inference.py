from fastapi.testclient import TestClient

from app import main as main_mod
from app.config import JoyaiConfig
from app.main import app

client = TestClient(app)


def test_healthz():
    r = client.get("/healthz")
    assert r.status_code == 200
    assert r.json()["status"] == "ok"
    assert r.json()["mode"] == "placeholder"


def test_healthz_reports_joyai_mode(monkeypatch):
    monkeypatch.setattr(main_mod, "_CONFIG", JoyaiConfig(
        base_url="http://joyai-vl:8000/v1", model="joyai-vl", api_key="",
        timeout_seconds=5.0, max_tokens=512,
    ))
    assert client.get("/healthz").json()["mode"] == "joyai"


def test_infer_one_frame_returns_valid_features():
    files = [("frames", ("a.jpg", b"\xff\xd8\xff\xe0fake", "image/jpeg"))]
    r = client.post("/infer", files=files)
    assert r.status_code == 200
    data = r.json()
    assert data["model"] == "placeholder-v0"
    assert 4.0 <= data["areaSqm"] <= 16.0
    assert data["safetyScore"] is not None and 0 <= data["safetyScore"] <= 100
    assert isinstance(data["recommendedActions"], list)
    assert len(data["recommendedActions"]) >= 1


def test_infer_deterministic_same_input_same_output():
    files = [("frames", ("a.jpg", b"\xff\xd8\xffdeterministic-bytes", "image/jpeg"))]
    r1 = client.post("/infer", files=files)
    r2 = client.post("/infer", files=files)
    assert r1.json() == r2.json()


def test_infer_rejects_non_image_content_type():
    files = [("frames", ("a.txt", b"hi", "text/plain"))]
    r = client.post("/infer", files=files)
    assert r.status_code == 400


def test_infer_rejects_empty_image():
    files = [("frames", ("a.jpg", b"", "image/jpeg"))]
    r = client.post("/infer", files=files)
    assert r.status_code == 400


def test_infer_rejects_spoofed_image_content_type():
    files = [("frames", ("a.jpg", b"not-an-image", "image/jpeg"))]
    r = client.post("/infer", files=files)
    assert r.status_code == 400


def test_infer_rejects_mismatched_image_type():
    files = [("frames", ("a.jpg", b"\x89PNG\r\n\x1a\nrest", "image/jpeg"))]
    r = client.post("/infer", files=files)
    assert r.status_code == 400


def test_infer_accepts_png_and_webp_signatures():
    for name, payload, mime in (
        ("a.png", b"\x89PNG\r\n\x1a\nrest", "image/png"),
        ("a.webp", b"RIFF\x04\x00\x00\x00WEBPrest", "image/webp"),
    ):
        r = client.post("/infer", files=[("frames", (name, payload, mime))])
        assert r.status_code == 200


def test_infer_rejects_frame_over_size_limit(monkeypatch):
    monkeypatch.setattr(main_mod, "MAX_FRAME_BYTES", 12)
    monkeypatch.setattr(main_mod, "MAX_TOTAL_BYTES", 100)
    payload = b"\xff\xd8\xff" + b"x" * 10
    r = client.post("/infer", files=[("frames", ("a.jpg", payload, "image/jpeg"))])
    assert r.status_code == 413


def test_infer_rejects_total_size_limit(monkeypatch):
    monkeypatch.setattr(main_mod, "MAX_FRAME_BYTES", 20)
    monkeypatch.setattr(main_mod, "MAX_TOTAL_BYTES", 20)
    payload = b"\xff\xd8\xff" + b"x" * 8
    files = [("frames", (f"{i}.jpg", payload, "image/jpeg")) for i in range(2)]
    r = client.post("/infer", files=files)
    assert r.status_code == 413


def test_infer_rejects_more_than_3():
    files = [("frames", (f"{i}.jpg", b"x", "image/jpeg")) for i in range(4)]
    r = client.post("/infer", files=files)
    assert r.status_code == 400


def test_jumpingjack_discouraged_in_small_room():
    # 用一组特定字节让面积偏小（hash 决定的）
    files = [("frames", ("a.jpg", b"\xff\xd8\xff\x00", "image/jpeg"))]
    r = client.post("/infer", files=files).json()
    if r["areaSqm"] < 10.0:
        actions = [d["action"] for d in r["discouragedActions"]]
        assert "jumpingJack" in actions
