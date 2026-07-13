# 远程自托管 JoyAI-VL（Linux GPU 机器，无 Docker）

适配场景：一台远程 **Linux + NVIDIA GPU**（如 RTX 5880 Ada 48GB）只跑模型，
`vision-svc` 在本地/别处，通过 `JOYAI_VL_BASE_URL` 连过去。

> 8B VLM bf16 权重约 16GB，48GB 显存非常宽裕，单卡即可，无需量化。

## 1. 远程机器：拉起 vLLM

把 `joyai-vl-serve.sh` 拷到远程机器并运行（脚本会用 `uv` 建 venv 装 vLLM）：

```bash
# 远程机器上
scp joyai-vl-serve.sh user@REMOTE:~/         # 或 git clone 整个仓库
ssh user@REMOTE
bash joyai-vl-serve.sh                        # 首次会下载 ~16GB 权重，耐心等
# 看到 "Uvicorn running on http://0.0.0.0:8000" 即就绪
```

常用覆盖项（环境变量）：

```bash
JOYAI_VL_PORT=8000 \
JOYAI_VL_MAX_LEN=8192 \
VLLM_VERSION=0.8.5 \              # JoyAI-VL 基座是 Qwen3-VL，需较新 vLLM；最新版报架构不识别时锁版本
HUGGING_FACE_HUB_TOKEN=hf_xxx \  # 受限模型才需要
bash joyai-vl-serve.sh
```

自检端点已起（在远程机器上）：

```bash
curl http://localhost:8000/v1/models      # 应列出 served-model-name: joyai-vl
```

## 2. 把端点暴露给 vision-svc

两选一：

**A. SSH 隧道（推荐，免开公网端口）** — 在 **vision-svc 所在机器**上：

```bash
ssh -N -L 8000:localhost:8000 user@REMOTE   # 本地 8000 → 远程 8000，保持此终端
```

此时 vision-svc 用 `http://localhost:8000/v1` 即可。

**B. 直连内网/公网 IP** — 远程机器放行 8000 端口，vision-svc 用 `http://REMOTE_IP:8000/v1`。
（公网暴露务必加鉴权/防火墙，vLLM 可用 `--api-key` 起，vision-svc 设 `JOYAI_VL_API_KEY` 对上。）

## 3. 让 vision-svc 用上它

```bash
# vision-svc 所在机器
export JOYAI_VL_BASE_URL=http://localhost:8000/v1   # 隧道方式
export JOYAI_VL_MODEL=joyai-vl                       # 与 serve 的 --served-model-name 一致
# 本地直接跑：
cd vision-svc && uvicorn app.main:app --reload --port 8081
```

未设 `JOYAI_VL_BASE_URL` 时，`/infer` 和 `/critique` 自动回落到占位实现，链路不断。

## 4. 真链路自检

`joyai-vl-smoke.py` 直接复用 vision-svc 的真实客户端代码（调用 + JSON 解析 + 映射全验），
而不只是 ping 端点：

```bash
cd vision-svc
JOYAI_VL_BASE_URL=http://localhost:8000/v1 python deploy/joyai-vl-smoke.py
# 或：python deploy/joyai-vl-smoke.py http://localhost:8000/v1
```

通过会打印 `/infer` 的房间识别结果和 `/critique` 的动作点评结果，末尾 `✓ JoyAI-VL 真链路自检通过`。

## 常见问题

| 现象 | 处理 |
|------|------|
| `torch.cuda not available` | 远程机器缺 NVIDIA 驱动/CUDA，先 `nvidia-smi` 确认 |
| `ImportError: libcudart.so.13` | PyPI 默认 vLLM 轮子按 CUDA 13 编译；驱动只到 CUDA 12.x 时，改装 GitHub Releases 上的 `+cu129` 变体轮子（可与 torch cu128 栈混用）：下载 `vllm-X.Y.Z+cu129-cp38-abi3-manylinux_2_28_x86_64.whl` 后 `uv pip install ./vllm-*.whl` 覆盖 |
| `--limit-mm-per-prompt` 解析报错 | 当前脚本已使用 vLLM 0.23+ 所需 JSON：`--limit-mm-per-prompt '{"image": 3}'`；若使用旧版 vLLM，请确认该版本的 CLI 兼容性 |
| vLLM 启动报模型架构不识别 | JoyAI-VL 基座 Qwen3-VL 较新，`VLLM_VERSION` 锁到支持该架构的版本 |
| OOM | 调小 `JOYAI_VL_MAX_LEN` 或 `JOYAI_VL_GPU_UTIL` |
| 下载慢/失败 | 设 `HF_ENDPOINT=https://hf-mirror.com` 走镜像，受限模型配 `HUGGING_FACE_HUB_TOKEN` |
| 隧道断 | 用 `autossh -M 0 -N -L 8000:localhost:8000 user@REMOTE` 保活 |
