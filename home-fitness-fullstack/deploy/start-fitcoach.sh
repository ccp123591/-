#!/bin/bash
# FitCoach 裸机部署启动脚本：vision-svc(8081) + backend(8082)
#
# 用法：
#   MIMO_API_KEY=tp-xxx ./start-fitcoach.sh
#   TUNNEL_URL=https://xxx.trycloudflare.com MIMO_API_KEY=tp-xxx ./start-fitcoach.sh
#
# TUNNEL_URL 也可以写进同目录的 tunnel-url.txt，脚本会自动读取并加入 CORS 白名单。

set -u

# —— 路径配置（按实际部署位置调整）—— #
APP_ROOT="${APP_ROOT:-/root/fitcoach/home-fitness-fullstack}"
VENV_BIN="${VENV_BIN:-/root/.venvs/vision/bin}"
BACKEND_JAR="${BACKEND_JAR:-target/fitcoach-backend-3.0.0.jar}"
LOG_DIR="${LOG_DIR:-/root}"

# —— CORS 白名单：本地端口 + 可选的公网隧道地址 —— #
CORS="http://localhost:5173,http://localhost:4173,http://localhost:8090"
TUNNEL_URL="${TUNNEL_URL:-}"
[ -z "$TUNNEL_URL" ] && [ -f "$LOG_DIR/tunnel-url.txt" ] && TUNNEL_URL="$(cat "$LOG_DIR/tunnel-url.txt")"
[ -n "$TUNNEL_URL" ] && CORS="$TUNNEL_URL,$CORS"

# —— vision-svc（视觉推理适配层，连接本机 JoyAI-VL vLLM）—— #
cd "$APP_ROOT/vision-svc" || exit 1
JOYAI_VL_BASE_URL="${JOYAI_VL_BASE_URL:-http://127.0.0.1:8000/v1}" \
JOYAI_VL_MODEL="${JOYAI_VL_MODEL:-joyai-vl}" \
nohup "$VENV_BIN/uvicorn" app.main:app --host 127.0.0.1 --port 8081 > "$LOG_DIR/vision.log" 2>&1 &

# —— backend（Spring Boot，dev profile + H2 内存库）—— #
# 注意：8080 常被其他服务占用，这里固定用 8082，需与 nginx 配置一致。
cd "$APP_ROOT/backend" || exit 1
SPRING_PROFILES_ACTIVE="${SPRING_PROFILES_ACTIVE:-dev}" \
SPRING_DATA_REDIS_HOST="${SPRING_DATA_REDIS_HOST:-127.0.0.1}" \
AI_COACH_PROVIDER="${AI_COACH_PROVIDER:-mimo}" \
MIMO_API_KEY="${MIMO_API_KEY:-}" \
MIMO_BASE_URL="${MIMO_BASE_URL:-https://token-plan-cn.xiaomimimo.com/v1}" \
MIMO_MODEL="${MIMO_MODEL:-mimo-v2.5}" \
AI_TTS_PROVIDER="${AI_TTS_PROVIDER:-mimo}" \
MIMO_TTS_BASE_URL="${MIMO_TTS_BASE_URL:-https://token-plan-cn.xiaomimimo.com/v1}" \
MIMO_TTS_MODEL="${MIMO_TTS_MODEL:-mimo-v2.5-tts}" \
MIMO_TTS_VOICE="${MIMO_TTS_VOICE:-mimo_default}" \
AI_ASR_PROVIDER="${AI_ASR_PROVIDER:-mimo}" \
MIMO_ASR_MODEL="${MIMO_ASR_MODEL:-mimo-v2.5-asr}" \
VISION_BASE_URL="${VISION_BASE_URL:-http://127.0.0.1:8081}" \
SECURITY_TRUST_PROXY_HEADERS=true \
CORS_ALLOWED_ORIGINS="$CORS" \
nohup java -Dfile.encoding=UTF-8 -Xms256m -Xmx1g -Dserver.port=8082 \
  -jar "$BACKEND_JAR" > "$LOG_DIR/backend.log" 2>&1 &

echo "started: vision-svc=:8081 backend=:8082"
echo "cors=$CORS"
