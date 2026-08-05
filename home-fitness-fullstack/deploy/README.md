# FitCoach 服务器部署指南

本目录包含裸机/VPS 服务器部署所需的配置和脚本（Docker Compose 部署见项目根目录的 `docker-compose.yml`）。

## 环境要求

- Ubuntu 20.04+ / Debian 11+
- JDK 17、Maven 3.6+
- Node.js 20+、npm
- Nginx、Redis
- Python 3.8+ (vision-svc)

## 快速部署

### 1. 安装依赖

```bash
sudo apt-get update
sudo DEBIAN_FRONTEND=noninteractive apt-get install -y openjdk-17-jdk maven nginx redis-server curl
```

### 2. 上传项目

```bash
# 克隆仓库
git clone <你的仓库地址> /opt/fitcoach
cd /opt/fitcoach/home-fitness-fullstack
```

### 3. 配置环境变量

编辑启动脚本 `deploy/start-fitcoach.sh`，或通过环境变量传入：

- `MIMO_API_KEY` — MiMo AI 教练的 API key (tp- 开头)
- `TUNNEL_URL` (可选) — Cloudflare 隧道地址，会自动加入 CORS 白名单

### 4. 构建

```bash
# 后端
cd backend
mvn clean package -DskipTests

# 前端
cd ../frontend
npm install
npm run build

# vision-svc
cd ../vision-svc
python3 -m venv /opt/fitcoach-venv
source /opt/fitcoach-venv/bin/activate
pip install -r requirements.txt
```

### 5. 配置 Nginx

```bash
sudo cp deploy/nginx-site.conf /etc/nginx/sites-available/fitcoach
sudo ln -sf /etc/nginx/sites-available/fitcoach /etc/nginx/sites-enabled/
sudo mkdir -p /var/www/fitcoach
sudo cp -r frontend/dist/* /var/www/fitcoach/
sudo nginx -t && sudo systemctl reload nginx
```

### 6. 启动服务

```bash
# 启动 Redis
sudo systemctl enable redis-server
sudo systemctl start redis-server

# 启动 FitCoach (vision-svc + backend)
chmod +x deploy/start-fitcoach.sh
nohup deploy/start-fitcoach.sh > fitcoach.log 2>&1 &
```

服务监听在：
- vision-svc: `127.0.0.1:8081`
- backend: `127.0.0.1:8082`
- nginx: `0.0.0.0:8090` (前端 + API 反向代理)

### 7. 开放公网访问（可选）

#### 方案 A：Cloudflare Tunnel (推荐，免费)

```bash
# 安装 cloudflared
curl -sL https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-linux-amd64 -o /usr/local/bin/cloudflared
chmod +x /usr/local/bin/cloudflared

# 启动隧道（HTTPS，无需域名备案）
nohup cloudflared tunnel --url http://localhost:8090 --protocol http2 > tunnel.log 2>&1 &

# 提取公网地址
grep -o "https://[a-z0-9-]*\.trycloudflare\.com" tunnel.log | head -1

# 将隧道地址写入 tunnel-url.txt，重启后端自动加入 CORS 白名单
echo "https://xxx.trycloudflare.com" > tunnel-url.txt
pkill -f fitcoach-backend
TUNNEL_URL=$(cat tunnel-url.txt) nohup deploy/start-fitcoach.sh > fitcoach.log 2>&1 &
```

**注意**：免费隧道重启后网址会变，需重新执行上述步骤。固定域名需 Cloudflare Zero Trust 配置。

#### 方案 B：直接开放端口

如果服务商支持端口映射，将 `8090` 映射到公网即可直接用 `http://IP:端口` 访问。

## 配置说明

### CORS 白名单

`deploy/start-fitcoach.sh` 会自动将以下来源加入后端 CORS 白名单：
- `http://localhost:5173` (前端开发服务器)
- `http://localhost:4173` (前端预览服务器)
- `http://localhost:8090` (nginx)
- `$TUNNEL_URL` (如果设置了该环境变量或存在 `tunnel-url.txt`)

### MiMo v2.5 配置

脚本已预配置 token-plan-cn 网关和 v2.5 模型：
- 网关: `https://token-plan-cn.xiaomimimo.com/v1`
- 对话模型: `mimo-v2.5`
- TTS 模型: `mimo-v2.5-tts`
- 默认音色: `mimo_default` (可选：冰糖、茉莉、苏打、白桦、Mia、Chloe、Milo、Dean)

`tp-` 开头的 key **仅在 token-plan-cn 网关有效**，用官方 `api.xiaomimimo.com` 会返回 401。

### 视觉推理 (JoyAI-VL)

如果你的服务器有 GPU，可以本地部署 JoyAI-VL vLLM：

```bash
# 安装 vLLM (需 CUDA 12+，显存 16GB+)
pip install vllm

# 启动 JoyAI-VL
nohup vllm serve jdopensource/JoyAI-VL-Interaction-Preview \
  --served-model-name joyai-vl \
  --host 0.0.0.0 --port 8000 \
  --trust-remote-code \
  --dtype bfloat16 \
  --max-model-len 8192 \
  --gpu-memory-utilization 0.90 \
  --limit-mm-per-prompt '{"image": 3}' \
  > vllm.log 2>&1 &
```

vision-svc 会自动连接 `http://127.0.0.1:8000/v1`。

## 故障排查

### 登录显示"服务器开小差"

检查：
1. 后端是否正常运行 `curl http://127.0.0.1:8082/actuator/health`
2. nginx 是否正确代理 `curl http://127.0.0.1:8090/api/auth/login/email`
3. 浏览器 F12 Console 是否有 CORS 错误（如果有，检查隧道地址是否在 CORS 白名单里）

### 前端一直显示"演示学员"

清除浏览器缓存：按 F12 → Application → Local Storage → 删除 `fitcoach_config` → 刷新页面。

### AI 教练返回 401

- 确认 `MIMO_API_KEY` 已设置且以 `tp-` 开头
- 检查后端日志 `tail -f fitcoach.log | grep -i mimo`
- v2.5 之前的旧模型名 (`mimo-v2-flash` / `mimo-v2-tts` / 音色 `default_zh`) 已下线，必须用新配置

### 手机端语音助手没反应

先确认 `AI_ASR_PROVIDER=mimo`（默认 `none` 时前端只给文字输入）。启动脚本已默认开启，
若手动传了环境变量请检查。仍然没反应时按顺序排查：

1. **必须是 HTTPS**：`getUserMedia` 只在安全上下文可用，用 IP + http 访问一定拿不到麦克风。
2. **nginx 的 `Permissions-Policy`**：`microphone` 必须是 `(self)`，写成空 `()` 等于彻底禁用，
   且不报错、只静默失效。
3. **后端日志**：`tail -f backend.log | grep -i asr`，401 说明 key 或网关不对。

注意浏览器原生的 `SpeechRecognition` 在国行 Android（无 GMS）和微信 WKWebView/X5 里
根本没有识别引擎，`start()` 后不触发任何回调 —— 这正是改走服务端 `mimo-v2.5-asr` 的原因，
复用同一个 `MIMO_API_KEY` 和网关，不需要额外部署模型。

## 相关文档

- [vision-svc 部署文档](../vision-svc/deploy/README.md)
- [Docker Compose 部署](../README.md#docker-compose-部署)
- [MiMo API 文档](https://platform.xiaomimimo.com/)
