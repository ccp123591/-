# vision-svc — FitCoach 环境建模推理服务

Python FastAPI 视觉适配服务，给 Java 后端的 `VisionClient` 提供环境识别、动作点评和场景摘要端点。

## 端点

- `GET /healthz` 健康检查，同时返回当前配置模式：`placeholder` 或 `joyai`
- `POST /infer` 接收 1-3 帧 multipart → `RoomFeatures`（训练前**环境识别**：面积/障碍/推荐动作/安全分）
- `POST /critique` 接收 `action` + 1-3 帧 multipart（可带 `reps`/`score`）→ `FormCritique`（**动作视觉点评**：看关键帧给自然语言反馈）
- `POST /scene` 接收 1-3 帧 multipart → `SceneSummary`（视频畅聊场景摘要）

三个推理端点都可连接 JoyAI-VL；未配置端点时服务会返回 `model=placeholder-v0` 供开发诊断。
Java 后端默认会拒绝将这类占位的**环境安全评估**入库，避免把 hash 生成的演示结果当成真实识别。

上传仅接受 JPEG/PNG/WebP，服务会校验文件签名；单帧最大 5 MiB，单请求图片总量最大 12 MiB。

## 推理路径与模块

| 模块 | 职责 |
|------|------|
| `inference.py` | `/infer` 调度 + 占位房间推理（hash 伪随机） |
| `critique.py` | `/critique` 调度 + 通用兜底点评 |
| `joyai_vl.py` | JoyAI-VL 真实推理（OpenAI 兼容视觉对话）：`analyze_frames_joyai` + `critique_frames_joyai` |
| `rules.py` | 共享确定性规则：`action_rules` / `safety_score`（安全逻辑不交给模型） |
| `config.py` | 环境变量驱动的不可变配置 |
| `deploy/` | 远程自托管脚本（`joyai-vl-serve.sh` / `joyai-vl-smoke.py`），见 `deploy/README.md` |

> 为什么有 `/critique`：动作捕捉本身由前端 MediaPipe Pose（端侧）完成，只给角度/计数；
> JoyAI-VL 补的是 MediaPipe 给不了的**语义层**——真看画面点评动作质量。

## 启用 JoyAI-VL（实验）

[JoyAI-VL](https://github.com/jd-opensource/JoyAI-VL-Interaction) 是京东开源的视觉语言模型
（Apache-2.0，基座 Qwen3-8B + Qwen3-VL ViT），由 vLLM 以 OpenAI 兼容协议提供服务。
配置端点后，`/infer` 改为把房间帧交给 JoyAI-VL 输出结构化房间事实，再走 `rules` 推导动作/安全分；
**端点不可用时视觉服务会降级为占位输出**；安全相关的环境扫描会由 Java 后端拒绝该结果并提示模型未配置。

```bash
# 环境变量（留空 = 占位推理）
export JOYAI_VL_BASE_URL=http://localhost:8000/v1   # OpenAI 兼容端点
export JOYAI_VL_MODEL=joyai-vl
export JOYAI_VL_API_KEY=                              # vLLM 自托管通常不需要
```

JoyAI-VL 只部署在远程 NVIDIA GPU + Linux 服务器；本机 Compose 不包含模型服务：

```bash
# 远程 Linux GPU 机器，无 Docker（推荐用于 RTX 5880 等远程机）
#   见 deploy/README.md —— uv 装 vLLM + SSH 隧道连过来 + 真链路自检
bash deploy/joyai-vl-serve.sh                        # 在远程机器上跑
```

把 `JOYAI_VL_BASE_URL` 指向远程 JoyAI-VL OpenAI 兼容端点；推荐通过 SSH 隧道或受保护的内网访问。

## 后续升级路径

1. 用真实房间数据集对 JoyAI-VL 的房间事实抽取做评测 / few-shot 调优
2. 备选轻量路线：`Intel/midas-small` ONNX 深度图 + `xuebinqin/U-2-Net` 障碍物分割 + MediaPipe Pose 尺度估计

## 本地跑

```bash
cd vision-svc
pip install -r requirements.txt
uvicorn app.main:app --reload --port 8081

# 测试
pytest -q
```

## Docker

被根目录 `docker-compose.yml` 拉起：

```bash
docker compose up -d --build vision-svc
curl http://localhost:8081/healthz
# {"status":"ok","mode":"placeholder"}；配置 JOYAI_VL_BASE_URL 后 mode=joyai
```
