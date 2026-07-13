"""FastAPI 启动模块。

- POST /infer    房间环境识别 → RoomFeatures（训练前扫房间）
- POST /critique 动作视觉点评 → FormCritique（训练中/后看关键帧点评动作）
- POST /scene    畅聊场景摘要 → SceneSummary（视频畅聊时看用户在干嘛）
三条路径都优先走 JoyAI-VL，端点未配置/不可用时各自优雅回落。
"""
from __future__ import annotations

import logging
from typing import List, Optional

from fastapi import FastAPI, File, Form, HTTPException, UploadFile
from fastapi.responses import JSONResponse

from .config import JoyaiConfig, load_joyai_config
from .critique import critique_frames
from .inference import analyze_frames
from .scene import describe_scene
from .schema import FormCritique, RoomFeatures, SceneSummary

logger = logging.getLogger("vision-svc")
logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(name)s: %(message)s")

app = FastAPI(title="FitCoach vision-svc", version="0.2.0")

# UploadFile uses a spooled temporary file, but an unbounded read can still move the
# complete payload into process memory. Keep both per-frame and request-wide caps.
MAX_FRAME_BYTES = 5 * 1024 * 1024
MAX_TOTAL_BYTES = 12 * 1024 * 1024

_CONFIG: JoyaiConfig = load_joyai_config()


@app.get("/healthz")
def healthz() -> dict:
    return {"status": "ok", "mode": "joyai" if _CONFIG.enabled else "placeholder"}


def _image_mime(payload: bytes) -> Optional[str]:
    """Return the MIME type identified from trusted file signature bytes."""
    if payload.startswith(b"\xff\xd8\xff"):
        return "image/jpeg"
    if payload.startswith(b"\x89PNG\r\n\x1a\n"):
        return "image/png"
    if len(payload) >= 12 and payload.startswith(b"RIFF") and payload[8:12] == b"WEBP":
        return "image/webp"
    return None


async def _read_frames(frames: List[UploadFile]) -> List[bytes]:
    """校验并有界读取 1-3 帧 JPEG/PNG/WebP 图片。"""
    if not frames:
        raise HTTPException(status_code=400, detail="No frames uploaded")
    if len(frames) > 3:
        raise HTTPException(status_code=400, detail="At most 3 frames")
    payloads: List[bytes] = []
    total_bytes = 0
    for f in frames:
        if not (f.content_type and f.content_type.startswith("image/")):
            raise HTTPException(status_code=400, detail=f"Unsupported content-type: {f.content_type}")
        payload = await f.read(MAX_FRAME_BYTES + 1)
        if not payload:
            raise HTTPException(status_code=400, detail="Empty image frame")
        if len(payload) > MAX_FRAME_BYTES:
            raise HTTPException(status_code=413, detail=f"Each frame must be <= {MAX_FRAME_BYTES} bytes")

        detected = _image_mime(payload)
        if detected is None:
            raise HTTPException(status_code=400, detail="Unsupported or invalid image data")

        declared = f.content_type.lower().split(";", 1)[0].strip()
        if declared == "image/jpg":
            declared = "image/jpeg"
        if declared != detected:
            raise HTTPException(status_code=400, detail="Image content-type does not match file data")

        total_bytes += len(payload)
        if total_bytes > MAX_TOTAL_BYTES:
            raise HTTPException(status_code=413, detail=f"Total frame payload must be <= {MAX_TOTAL_BYTES} bytes")
        payloads.append(payload)
    return payloads


@app.post("/infer", response_model=RoomFeatures)
async def infer(frames: List[UploadFile] = File(...)) -> RoomFeatures:
    payloads = await _read_frames(frames)
    try:
        result = analyze_frames(payloads)
        logger.info("inference ok: model=%s area=%.2f safety=%s",
                    result.model, result.areaSqm or 0.0, result.safetyScore)
        return result
    except Exception as e:  # pragma: no cover - defensive
        logger.exception("inference failed: %s", e)
        return JSONResponse(status_code=500, content={"detail": "inference failure"})


@app.post("/scene", response_model=SceneSummary)
async def scene(frames: List[UploadFile] = File(...)) -> SceneSummary:
    payloads = await _read_frames(frames)
    try:
        result = describe_scene(payloads)
        logger.info("scene ok: model=%s person=%s", result.model, result.personPresent)
        return result
    except Exception as e:  # pragma: no cover - defensive
        logger.exception("scene failed: %s", e)
        return JSONResponse(status_code=500, content={"detail": "scene failure"})


@app.post("/critique", response_model=FormCritique)
async def critique(
    action: str = Form(...),
    reps: Optional[int] = Form(None),
    score: Optional[int] = Form(None),
    frames: List[UploadFile] = File(...),
) -> FormCritique:
    if not action.strip():
        raise HTTPException(status_code=400, detail="action is required")
    payloads = await _read_frames(frames)
    metrics = {"reps": reps, "score": score}
    try:
        result = critique_frames(payloads, action.strip(), metrics)
        logger.info("critique ok: model=%s action=%s issues=%d",
                    result.model, result.action, len(result.issues))
        return result
    except Exception as e:  # pragma: no cover - defensive
        logger.exception("critique failed: %s", e)
        return JSONResponse(status_code=500, content={"detail": "critique failure"})
