"""FastAPI 启动模块。

- POST /infer    房间环境识别 → RoomFeatures（训练前扫房间）
- POST /critique 动作视觉点评 → FormCritique（训练中/后看关键帧点评动作）
两条路径都优先走 JoyAI-VL，端点未配置/不可用时各自优雅回落。
"""
from __future__ import annotations

import logging
from typing import List, Optional

from fastapi import FastAPI, File, Form, HTTPException, UploadFile
from fastapi.responses import JSONResponse

from .critique import critique_frames
from .inference import analyze_frames
from .schema import FormCritique, RoomFeatures

logger = logging.getLogger("vision-svc")
logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(name)s: %(message)s")

app = FastAPI(title="FitCoach vision-svc", version="0.2.0")


@app.get("/healthz")
def healthz() -> dict:
    return {"status": "ok"}


async def _read_frames(frames: List[UploadFile]) -> List[bytes]:
    """校验并读取 1-3 帧图片字节（/infer 与 /critique 共用）。"""
    if not frames:
        raise HTTPException(status_code=400, detail="No frames uploaded")
    if len(frames) > 3:
        raise HTTPException(status_code=400, detail="At most 3 frames")
    payloads: List[bytes] = []
    for f in frames:
        if not (f.content_type and f.content_type.startswith("image/")):
            raise HTTPException(status_code=400, detail=f"Unsupported content-type: {f.content_type}")
        payloads.append(await f.read())
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
