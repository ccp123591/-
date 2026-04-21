# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Repository Layout

This directory holds two related projects plus competition submission docs (`.docx`):

- `home-fitness-fullstack/` — **primary active project.** Vue 3 PWA frontend + Spring Boot 3.2 backend. All new work goes here.
- `home-fitness-pwa/` — earlier standalone PWA (plain HTML/JS, no build step, no backend). Reference only; do not edit unless explicitly asked.

Both are implementations of **FitCoach**, a browser-based AI home-fitness coach using MediaPipe Pose for real-time posture detection, scoring, TTS voice coaching, and (in the fullstack version) Claude-powered feedback.

## Commands (home-fitness-fullstack)

One-shot launch (Windows): `启动.bat` at the project root starts backend + frontend in separate terminals and opens the browser.

### Backend (`backend/`, Spring Boot 3.2, Java 17)
```bash
cd backend
mvn spring-boot:run                                    # dev profile (H2 in-memory, default)
mvn spring-boot:run -Dspring-boot.run.profiles=prod    # MySQL — requires DB_HOST / DB_USER / DB_PASSWORD env vars
mvn test                                               # run tests
mvn -Dtest=ClassName#method test                       # single test
mvn clean package                                      # build jar into target/
```
- API base: `http://localhost:8080`
- Swagger UI: `http://localhost:8080/swagger-ui.html`
- H2 console: `http://localhost:8080/h2-console` (JDBC `jdbc:h2:mem:fitcoach`, user `sa`, empty password)
- Seed accounts: `admin@fitcoach.com` / `admin123`, `demo@fitcoach.com` / `admin123`

### Frontend (`frontend/`, Vue 3 + Vite 5)
```bash
cd frontend
npm install
npm run dev        # http://localhost:5173, proxies /api → http://localhost:8080
npm run build      # production build to dist/
npm run preview    # preview built output on :4173
```
No test runner, linter, or TypeScript is configured — don't assume `npm test` / `npm run lint` exists.

## Architecture (home-fitness-fullstack)

### Big picture
Browser (Vue 3 PWA) runs MediaPipe Pose locally for real-time skeleton detection, counting, and scoring. Sessions are persisted first to **IndexedDB** (offline-first), then synced to the backend over REST/JSON. The backend is a domain-sliced Spring Boot monolith exposing `/api/**` with JWT stateless auth; AI coach feedback is proxied to Claude via a server-side service.

### Backend domain structure
Java package `com.fitcoach` is split by **bounded context**, not by layer — each domain folder holds its own Controller / Service / Entity / Repository:

```
auth  user  session  coach  plan  badge  leaderboard  social  exercise  admin
```

Cross-cutting packages:
- `common/` — `ApiResult<T>` unified response wrapper (`code`/`message`/`data`/`timestamp`), `PageResult<T>`
- `security/` — `SecurityConfig` (stateless JWT, BCrypt), `JwtUtil`, `JwtAuthFilter`
- `config/` — CORS and OpenAPI config
- `exception/` — global `@RestControllerAdvice`

**Conventions to follow:**
- Every controller method returns `ApiResult<T>` — use `ApiResult.ok(data)` / `ApiResult.fail(msg)`, don't invent new envelopes.
- All endpoints are under `/api/**`. Public paths (whitelisted in `SecurityConfig`): `/api/auth/**`, `/api/exercises`, `/api/exercises/**`, `/api/plans/official`, `/api/leaderboard/**`, plus Swagger and H2. `/api/admin/**` requires `ROLE_ADMIN`; everything else needs a valid JWT.
- Entities use Spring Data JPA with `ddl-auto: update` — schema evolves from `@Entity` classes, no migrations tool configured.
- As of this writing most Controllers are **placeholders** (`🟡 占位`) with Swagger `@Operation` annotations but no business logic. The architecture is complete; services need to be filled in.
- AI coach provider is switchable via `ai.coach.provider` in `application.yml` (`mock` | `claude` | `openai`). Model default is `claude-haiku-4-5-20251001`. Use `mock` for local work unless a key is configured.
- JWT: access token 2h, refresh token 30d. Secret in `application.yml` is a dev default — never ship as-is.

### Frontend structure
```
src/
├── api/          backend API wrappers (axios)
├── modules/      core business logic (framework-agnostic JS)
│                   pose.js      MediaPipe Pose loader + skeleton draw
│                   exercise.js  joint-angle calc, state-machine rep counting, scoring
│                   voice.js     Web Speech TTS + Web Audio metronome
│                   storage.js   IndexedDB (sessions) + localStorage (config)
│                   poster.js    end-of-session report rendering
├── stores/       Pinia: auth, config, training, app
├── composables/  shared reactive logic
├── components/   layout / common / training / charts
├── views/        page-level components (Train, Records, Plans, Leaderboard, Feed, ...)
└── router/       Vue Router + auth guards
```

**Conventions to follow:**
- Path alias `@` → `src/` (configured in `vite.config.js` and `jsconfig.json`).
- Global CSS load order in `main.js` is significant: `base → themes → animations → responsive`. Don't reorder.
- Keep MediaPipe / scoring logic inside `modules/` so it stays decoupled from Vue — the older `home-fitness-pwa` uses the same algorithms in plain JS.
- Offline-first data flow: session complete → IndexedDB → `POST /api/sessions` on reconnect → `POST /api/coach/feedback` for AI review. Do not remove the IndexedDB layer.
- Responsive breakpoints are behavioral, not just visual: `<768px` single-column + bottom tabs, `768–1024px` two-column training layout, `>1024px` sidebar + content.
- PWA: `public/sw.js` is registered in `main.js`. Bumping asset paths requires cache-version bumps in the SW.

### Frontend ↔ backend contract
Vite dev server proxies `/api` → `http://localhost:8080` (see `vite.config.js`). CORS on the backend whitelists `localhost:5173`, `:4173`, `:8080`. When adding endpoints, keep the `/api/` prefix so the proxy and security rules both apply.

## home-fitness-pwa (legacy)

Pure static site — `index.html` + `css/` + `js/` + `sw.js` + `manifest.json`. Serve via any HTTP server (`python -m http.server 8080`, `npx serve .`) — camera APIs require HTTPS or localhost. No build step. Same scoring algorithms as the fullstack frontend's `modules/`, but in vanilla JS.
