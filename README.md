# Poker Game

Play-money online poker. Texas Hold'em first, then Omaha, Pineapple, 5-Card Draw, and Razz.

This repository is in **Phase 20**: all five variants are playable. The UI has **no login screen** — it auto-creates a guest session (`POST /api/auth/guest`) for embed / other-platform integration.

## Stack

- Frontend: React, Vite, TypeScript, Tailwind CSS, Zustand, React Router
- Backend: Spring Boot 4, Java 21, Maven
- Database: MongoDB 7 (Docker)

Virtual chips only. No real-money betting.

## Local development

Copy environment values, then start MongoDB, the API, and the UI.

```bash
copy .env.example .env
docker compose up -d mongo
```

### Backend

```bash
cd backend
mvnw.cmd clean test
mvnw.cmd spring-boot:run
```

API status: http://localhost:8080/api/status  
Health: http://localhost:8080/actuator/health  
Swagger: http://localhost:8080/swagger-ui.html

### Frontend

```bash
cd frontend
npm install
npm test
npm run dev
```

UI: http://localhost:5173 (or the next free port if 5173 is taken)

The Vite dev server proxies `/api` and `/ws` to Spring Boot. Sign in at `/login` or `/register`. `/lobby` lists tables, create/join/leave, and you can pick Texas Hold'em, Omaha, Pineapple, 5-Card Draw, or Razz. `/game/:gameId` is the live table, including showdown results from the server. A dropped socket reconnects automatically; disconnect does not fold. Omaha showdown uses exactly 2 hole cards and 3 community cards.

### Docker (all services)

```bash
docker compose up -d
docker compose down
```

## Layout

- `frontend/` — React application
- `backend/` — Spring Boot application and Texas Hold'em engine
- `docs/` — architecture, API, rules, and development notes
- `docker-compose.yml` — MongoDB, backend, and frontend

## Current endpoints

- `GET /api/status` — process is running
- `GET /actuator/health` — process and MongoDB
- `POST /api/auth/register` / `POST /api/auth/login` — JWT
- `GET /api/auth/me` — current user (Bearer token)
- `GET /api/tables` / `POST /api/tables` — list and create (JWT)
- `GET /api/tables/{id}` — table detail
- `POST /api/tables/{id}/join` / `leave` / `start`
- `GET /api/tables/{id}/state` — sanitized game state for the current user
- `ws://localhost:8080/ws` — STOMP; CONNECT with Bearer JWT; subscribe `/user/queue/game/{tableId}`
- Swagger UI — http://localhost:8080/swagger-ui.html

Dev users (profile `dev`): `player1`–`player6`, password `password`.

All five variants from the build spec are playable (Hold'em, Omaha, Pineapple, 5-Card Draw, Razz). Phase 20 audit notes and deployment checklist live in `docs/development.md` and the production-readiness canvas.
