# Development

## Prerequisites

- Java 21
- Node.js 20+
- Docker Desktop
- Maven Wrapper (included in `backend/`)

## Environment

Copy `.env.example` to `.env`. Do not commit secrets.

Frontend Vite variables live in `frontend/.env.example`. For local `npm run dev`, `frontend/.env.development` uses an empty `VITE_API_URL` so `/api` and `/ws` are proxied to Spring Boot. `VITE_WS_URL=ws://localhost:8080` talks to the API WebSocket directly.

## Start order

1. `docker compose up -d mongo`
2. `cd backend && mvnw.cmd spring-boot:run`
3. `cd frontend && npm install && npm run dev`

## Tests

```bash
cd backend
mvnw.cmd test
```

```bash
cd frontend
npm test
```

Backend integration tests start MongoDB with Testcontainers (Docker required). Frontend unit tests use Vitest and React Testing Library.

## After each phase

Compile, run tests, fix failures, then update these docs. Do not start the next phase with failing tests.

## Known limitations (Phase 20)

- Live hand state (deck, bets, streets) is in-memory per JVM; restart mid-hand loses the hand.
- Not multi-instance safe.
- Razz uses private 3→7 streets (no face-up / bring-in) in this play-money build.
- CORS and WebSocket allowed origins are localhost-oriented.
- Virtual chips only.

## Production deploy notes

- Docker Compose sets `SPRING_PROFILES_ACTIVE=prod` and requires `JWT_SECRET` in `.env`.
- Non-dev profiles reject placeholder JWT secrets.
- Prod disables Swagger and restricts health detail.
- Local `mvnw spring-boot:run` still defaults to `dev` (seeded `player1`–`player6` / `password`).

## Phase 20 complete when

- Production-readiness audit run; real blockers fixed (discard event privacy, requestId after apply, prod JWT/profile)
- Backend and frontend clean tests/builds pass
- Docs list known limitations and deployment checklist
- Audit canvas delivered

## Phase 19 complete when

- Lobby create can choose Razz
- Razz deals 3 cards, betting on 3rd–7th street with one private card each street, then low showdown
- Dedicated Razz evaluator: Aces low; straights/flushes ignored; pairs bad; wheel beats higher lows
- Existing Hold'em, Omaha, Pineapple, and Draw tests still pass

## Phase 18 complete when

- Lobby create can choose 5-Card Draw
- Draw deals 5 hole cards, first betting, draw/replace (0..maxDrawCount), second betting, showdown
- No community cards; highest normal 5-card hand wins
- `max-draw-count` is configurable via `app.table.max-draw-count` (default 5)
- Existing Texas Hold'em, Omaha, and Pineapple tests still pass
- Draw UI lets the acting player multi-select cards (or stand pat) and confirm

## Phase 17 complete when

- Lobby create can choose Pineapple
- Pineapple deals 3 hole cards and enters a discard phase before pre-flop betting
- Each player discards exactly 1; the discarded card never becomes a community card
- After discard, play continues like Texas Hold'em with 2 hole cards
- Existing Texas Hold'em and Omaha tests still pass
- Discard UI lets the acting player click a hole card to discard
