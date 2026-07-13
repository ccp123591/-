#!/usr/bin/env bash
# 在远程 Linux GPU 机器（如 RTX 5880 Ada 48GB）上启动 JoyAI-VL。
# 用 uv 装 vLLM，以 OpenAI 兼容协议 serve，监听 0.0.0.0:8000。
#
#   bash joyai-vl-serve.sh
#
# 可用环境变量覆盖默认值：
#   JOYAI_VL_HF_MODEL   HF 模型仓库（默认京东开源预览版）
#   JOYAI_VL_MODEL      served-model-name（默认 joyai-vl，需与 vision-svc 的 JOYAI_VL_MODEL 一致）
#   JOYAI_VL_PORT       端口（默认 8000）
#   JOYAI_VL_MAX_LEN    max-model-len（默认 8192）
#   JOYAI_VL_GPU_UTIL   显存利用率（默认 0.90）
#   VLLM_VERSION        锁定 vLLM 版本（留空=最新；JoyAI-VL 基座 Qwen3-VL 需较新 vLLM）
#   HUGGING_FACE_HUB_TOKEN  受限模型需要
set -euo pipefail

MODEL="${JOYAI_VL_HF_MODEL:-jdopensource/JoyAI-VL-Interaction-Preview}"
SERVED_NAME="${JOYAI_VL_MODEL:-joyai-vl}"
PORT="${JOYAI_VL_PORT:-8000}"
MAX_LEN="${JOYAI_VL_MAX_LEN:-8192}"
GPU_UTIL="${JOYAI_VL_GPU_UTIL:-0.90}"
VENV="${JOYAI_VL_VENV:-$HOME/.venvs/joyai-vl}"

# 1) 确保 uv 可用
if ! command -v uv >/dev/null 2>&1; then
  echo "[joyai] installing uv ..."
  curl -LsSf https://astral.sh/uv/install.sh | sh
  export PATH="$HOME/.local/bin:$PATH"
fi

# 2) venv + 安装 vLLM（Python 3.11）
if [ ! -d "$VENV" ]; then
  echo "[joyai] creating venv at $VENV ..."
  uv venv --python 3.11 "$VENV"
fi
# shellcheck disable=SC1091
source "$VENV/bin/activate"

if [ -n "${VLLM_VERSION:-}" ]; then
  echo "[joyai] installing vllm==$VLLM_VERSION ..."
  uv pip install "vllm==$VLLM_VERSION"
else
  echo "[joyai] installing latest vllm ..."
  uv pip install -U vllm
fi

# 3) GPU 自检（确认驱动 + bf16）
python - <<'PY'
import torch
assert torch.cuda.is_available(), "CUDA 不可用：检查 NVIDIA 驱动 / CUDA 运行时"
p = torch.cuda.get_device_properties(0)
print(f"[joyai] GPU: {torch.cuda.get_device_name(0)}  "
      f"VRAM={p.total_memory/1e9:.1f}GB  bf16={torch.cuda.is_bf16_supported()}")
PY

# 4) 启动 vLLM（OpenAI 兼容 /v1）
echo "[joyai] serving '$MODEL' as '$SERVED_NAME' on 0.0.0.0:$PORT ..."
exec vllm serve "$MODEL" \
  --served-model-name "$SERVED_NAME" \
  --host 0.0.0.0 \
  --port "$PORT" \
  --trust-remote-code \
  --dtype bfloat16 \
  --max-model-len "$MAX_LEN" \
  --gpu-memory-utilization "$GPU_UTIL" \
  --limit-mm-per-prompt '{"image": 3}'
