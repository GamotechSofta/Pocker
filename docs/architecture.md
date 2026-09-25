# Architecture

The backend is the authoritative game server. The React UI only displays sanitized state and sends player actions.

```text
Poker Rules
     ↓
Game Engine
     ↓
Game State
     ↓
REST + WebSocket
     ↓
React State
     ↓
React UI
```

## Services

| Service | Technology | Phase 20 status |
| --- | --- | --- |
| frontend | React + Vite + TypeScript + Tailwind + Zustand | All five variants; reconnect and state resync |
| backend | Spring Boot 4.1 + Java 21 | Auth, engine, lobby REST, STOMP; prod profile hardening |
| mongo | MongoDB 7 | `users`, `poker_tables` (players embedded) |

## Persistence

Indexes are created by Spring Data MongoDB (`auto-index-creation: true`).

- `V1__enable_pgcrypto.sql`
- `V2__create_users.sql`
- `V3__create_poker_tables.sql` (`poker_tables`, `poker_table_players`)

Passwords are stored only as BCrypt hashes. JWT access tokens are signed with `JWT_SECRET` (HS256). `/api/auth/me` and future private APIs require `Authorization: Bearer`.

Dev profile seeds `player1`–`player6` with password `password` if they do not already exist. Virtual chip stacks are stored per seated player (`startingChips` from `TableConfig` / `app.table`). The live engine is still the source of truth during a hand.

## Card engine

Plain Java under `com.poker.game.model`, testable without Spring:

- `Rank` / `Suit` / `Card` — 52 unique cards, codes such as `AS` and `KH`
- `Deck` — `createDeck()`, `shuffle()` with `SecureRandom`, `draw()`, `drawMultiple()`, `remainingCards()`, `reset()`

The deck is never exposed through HTTP. Community-card dealing, betting, and showdown live in `TexasHoldemEngine` with `TexasHoldemVariant` or `OmahaVariant`.

## Hand evaluator

Plain Java under `com.poker.game.evaluator`. It scores 5 cards, or the best 5 of 6–7 (Hold'em). Omaha uses `evaluateOmaha`: every combination of exactly 2 hole cards and 3 board cards, then the same 5-card rankings. Tie-breaks use `rankingValues` (kickers), not category alone. Ace-low A-2-3-4-5 is a five-high straight.

## Texas Hold'em engine

Plain Java under `com.poker.game.engine`, testable without Spring or WebSocket.

- `TableConfig` — small/big blind, starting chips, max players, max draw count (defaults 10/20/1000/6/5)
- `TexasHoldemEngine` — seats, dealer button, blinds, hole cards, streets, betting, draw, showdown, next hand
- `TurnManager` — whose turn; out-of-turn actions are rejected
- `PotManager` — main pot, side pots, splits, odd chip to the left of the dealer
- `TexasHoldemVariant` — 2 hole cards, 5 community, best 5 of 7
- `OmahaVariant` — 4 hole cards, 5 community, exactly 2 hole + 3 board
- `PineappleVariant` — 3 hole cards, discard 1 before flop, then like Hold'em
- `FiveCardDrawVariant` — 5 hole cards, no community; bet → draw → bet → showdown
- `RazzVariant` — 3→7 private cards; dedicated low evaluator (Aces low)

Actions: fold, check, call, bet, raise, all-in, discard (Pineapple / Draw). Incomplete all-in raises do not reopen a full re-raise. If everyone else folds, the remaining player wins without showdown.

Live play is STOMP over WebSocket. `/game/:gameId` renders the latest sanitized snapshot (REST load, then STOMP updates). Fold, check, call, bet, raise, and all-in send `/app/game/{tableId}/action` with only the legal options the server listed. Errors arrive on `/user/queue/errors`. Showdown ranks, revealed hole cards, and payouts come from that snapshot. The client does not pick winners.

## Lobby REST

Authenticated JWT endpoints under `/api/tables`:

- list, create, get, join, leave, start
- create `variant` is `TEXAS_HOLDEM` (default), `OMAHA`, `PINEAPPLE`, `FIVE_CARD_DRAW`, or `RAZZ`
- `GET /api/tables/{id}/state` returns a **per-viewer** snapshot

Opponent hole cards are `HIDDEN` until showdown. The deck is never in the response. Table metadata lives in MongoDB; `TexasHoldemEngine` instances stay in memory for the running process.

## Backend packages

Controllers stay thin. Poker logic will live under `com.poker.game` and must remain testable without Spring or WebSocket.

```text
Controller → Service → Poker Engine → Variant Rules → Evaluator → Pot Manager → Game State
```

## Real-time (STOMP)

Lobby and table management stay on REST. Live gameplay uses STOMP over WebSocket at `/ws`.

- CONNECT requires JWT (`Authorization: Bearer` or handshake `access_token`)
- Subscribe `/user/queue/game/{tableId}` and `/user/queue/errors`
- Actions: `SEND /app/game/{tableId}/action` with `{ action, amount?, requestId? }`
- The server broadcasts a **per-user** sanitized snapshot (`convertAndSendToUser`). There is no shared topic that includes real hole cards.
- Duplicate `requestId` values are ignored (idempotent)
- Disconnect sets `connected=false`; it does not fold. Re-subscribe authenticates with the current JWT, marks the player connected, and the server sends a sanitized `GAME_STATE`. A true reconnect (connected after `false`) also emits `PLAYER_RECONNECTED`.
- Action errors are `ERROR` payloads on the user queues, not STOMP ERROR frames that drop the socket.

## Frontend foundation

React under `frontend/src`:

- `routes/` — `/`, `/login`, `/register`, `/lobby`, `/game/:gameId`, `/profile`
- `store/` — `authStore`, `lobbyStore`, `gameStore`, `uiStore` (Zustand). Game state is the latest sanitized server snapshot.
- `api/` — REST client for auth, tables, and status. JWT from `localStorage` (`poker.accessToken`).
- `services/gameSocket.ts` — STOMP over `/ws`; per-user queues; auto-reconnect (2s–16s), JWT refresh on CONNECT, resubscribe, REST resync on later connects
- `layouts/` and `components/ui/` — shared shell

Login and register call the real `/api/auth` APIs. JWT is stored in `localStorage` (`poker.accessToken`). `/lobby`, `/profile`, and `/game/:gameId` require a session. The lobby lists tables via `GET /api/tables` then `GET /api/tables/{id}` for seated players, and create/join/leave hit the matching POST endpoints. Create can choose Texas Hold'em or Omaha. Join is only offered for `WAITING` tables that are not full. **Open table** loads `/game/:gameId`, which fetches sanitized state and subscribes to `/user/queue/game/{tableId}`. The UI never computes winners or legal actions itself.

## Security

Shuffle, dealing, turns, legal actions, pots, evaluation, winners, and chip balances are server-side. Passwords are BCrypt-hashed. JWTs are issued on register/login and validated by Spring Security. Browser CORS allows `http://localhost:*` and `http://127.0.0.1:*`.
