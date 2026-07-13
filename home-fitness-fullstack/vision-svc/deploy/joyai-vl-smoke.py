#!/usr/bin/env python3
"""对接好的 JoyAI-VL 端点做一次真链路自检。

直接复用 vision-svc 的真实客户端代码（analyze_frames_joyai / critique_frames_joyai），
所以这同时验证了"调用 + JSON 解析 + 映射"整条路径，而不只是端点连通性。

用法（在 vision-svc 目录下运行）：
    JOYAI_VL_BASE_URL=http://localhost:8000/v1 python deploy/joyai-vl-smoke.py
    # 或显式传 base-url：
    python deploy/joyai-vl-smoke.py http://localhost:8000/v1
"""
from __future__ import annotations

import os
import struct
import sys
import zlib
from pathlib import Path

# Windows 控制台默认 GBK，输出含 ✓/✗ 等符号会报错 —— 强制 UTF-8
try:
    sys.stdout.reconfigure(encoding="utf-8")  # type: ignore[attr-defined]
except Exception:
    pass

# 允许从 vision-svc 根目录直接跑（把它加入 import 路径）
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from app.config import load_joyai_config  # noqa: E402
from app.joyai_vl import analyze_frames_joyai, critique_frames_joyai  # noqa: E402


def _make_png(width: int = 256, height: int = 256) -> bytes:
    """用标准库生成一张合成 PNG（灰色地面 + 一块深色'障碍物'），无需 Pillow。"""
    def chunk(typ: bytes, data: bytes) -> bytes:
        return (struct.pack(">I", len(data)) + typ + data
                + struct.pack(">I", zlib.crc32(typ + data) & 0xFFFFFFFF))

    raw = bytearray()
    for y in range(height):
        raw.append(0)  # filter type 0
        for x in range(width):
            if 150 <= x <= 220 and 120 <= y <= 210:      # 一块深色矩形当障碍物
                raw += bytes((60, 60, 70))
            else:                                         # 浅灰地面
                raw += bytes((180, 178, 175))
    ihdr = struct.pack(">IIBBBBB", width, height, 8, 2, 0, 0, 0)  # 8-bit RGB
    return (b"\x89PNG\r\n\x1a\n"
            + chunk(b"IHDR", ihdr)
            + chunk(b"IDAT", zlib.compress(bytes(raw), 6))
            + chunk(b"IEND", b""))


def main() -> int:
    if len(sys.argv) > 1:
        os.environ["JOYAI_VL_BASE_URL"] = sys.argv[1]
    config = load_joyai_config()
    if not config.enabled:
        print("✗ 未设置 JOYAI_VL_BASE_URL —— 无法自检。"
              "\n  例：JOYAI_VL_BASE_URL=http://localhost:8000/v1 python deploy/joyai-vl-smoke.py")
        return 2

    print(f"→ 端点 {config.base_url}  模型 {config.model}")
    png = _make_png()

    print("\n[1/2] /infer 房间识别 ...")
    feats = analyze_frames_joyai([png], config)
    print(f"    model={feats.model}  area={feats.areaSqm}㎡  room={feats.roomType}  "
          f"lighting={feats.lighting}  obstacles={len(feats.obstacles)}  safety={feats.safetyScore}")
    print(f"    recommended={feats.recommendedActions}")

    print("\n[2/2] /critique 动作点评（squat）...")
    crit = critique_frames_joyai([png], "squat", {"reps": 12, "score": 80}, config)
    print(f"    model={crit.model}  formScore={crit.formScore}  issues={len(crit.issues)}")
    print(f"    summary={crit.summary}")
    for it in crit.issues:
        print(f"      - [{it.severity}] {it.joint}: {it.detail}")
    for tip in crit.tips:
        print(f"      · tip: {tip}")

    print("\n✓ JoyAI-VL 真链路自检通过")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
