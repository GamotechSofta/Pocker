# API

Phase 20 is the production-readiness audit. Variant `TEXAS_HOLDEM` (default), `OMAHA`, `PINEAPPLE`, `FIVE_CARD_DRAW`, or `RAZZ`. Subscribe always receives a sanitized `GAME_STATE`. Winners are never computed in the browser.

Embedded / no-login play: `POST /api/auth/guest` issues a guest JWT (no password UI). The React app calls this automatically on load.

## Status

`GET /api/status` — public process check.

## Health

`GET /actuator/health` — public, includes MongoDB.

Also: `/actuator/info`, `/actuator/metrics`.

## Authentication

### Register

`POST /api/auth/register`

```json
{
  "username": "player1",
  "email": "player1@poker.local",
  "password": "password1"
}
```

Returns `201` with a JWT and the user (never the password hash).

Username: 3–32 characters, letters, numbers, underscore. Email must be valid. Password: 8–72 characters. Usernames and emails are stored lowercase.

### Login

`POST /api/auth/login`

`username` may be the username or the email.

```json
{
  "username": "player1",
  "password": "password"
}
```

Wrong credentials always return `401` / `INVALID_CREDENTIALS` with the same message.

### Current user

`GET /api/auth/me`

Requires `Authorization: Bearer <token>`.

```json
{
  "id": "...",
  "username": "player1",
  "email": "player1@poker.local",
  "createdAt": "2026-09-21T11:00:00Z"
}
```

## Tables (JWT required)

Defaults if omitted on create: small blind 10, big blind 20, starting chips 1000, max players 6, variant `TEXAS_HOLDEM`. `variant` may also be `OMAHA`, `PINEAPPLE`, `FIVE_CARD_DRAW`, or `RAZZ`. House max draw count for 5-Card Draw defaults to `app.table.max-draw-count` (5).

### List

`GET /api/tables`

### Create

`POST /api/tables` — `201`

```json
{
  "name": "Omaha 10/20",
  "variant": "OMAHA",
  "smallBlind": 10,
  "bigBlind": 20,
  "maxPlayers": 6,
  "startingChips": 1000
}
```

### Get

`GET /api/tables/{id}`

Lobby detail: name, blinds, seats, chip stacks. No hole cards.

### Join

`POST /api/tables/{id}/join`

```json
{ "seat": 2 }
```

`seat` is optional; the server assigns the first empty seat.

A player may sit at only one table. Taken seats return `409` / `SEAT_UNAVAILABLE`.

### Leave

`POST /api/tables/{id}/leave`

Not allowed in the middle of a betting round (`409` / `GAME_ALREADY_STARTED`).

### Start

`POST /api/tables/{id}/start`

Requires two seated players with chips. Returns sanitized state for the caller. Starting again while a hand is live returns `409` / `GAME_ALREADY_STARTED`.

### State

`GET /api/tables/{id}/state`

Per-viewer snapshot. You see your hole cards (2 for Hold'em, 4 for Omaha, 3 then 2 for Pineapple, 5 for Draw, 3→7 for Razz). Opponents see `HIDDEN` until showdown. Community cards, pot, turn, and legal actions for **you** are included. The deck is never returned. Omaha winners use exactly 2 hole cards and 3 community cards. Pineapple requires a `DISCARD` action (`amount` = hole-card index) before pre-flop betting. 5-Card Draw has no community cards: after first betting, status `DRAW` accepts `DISCARD` with `indices` (0-based hole indexes to replace; empty = stand pat; at most `maxDrawCount`), then `SECOND_BET`, then showdown on the five hole cards. Razz has no community cards: statuses `THIRD_STREET`…`SEVENTH_STREET` deal private cards up to 7; showdown uses a dedicated low evaluator (Aces low; pairs bad; straights/flushes ignored).

## Error body

```json
{
  "timestamp": "2026-09-21T11:00:00Z",
  "status": 401,
  "code": "UNAUTHORIZED",
  "message": "Authentication is required.",
  "path": "/api/auth/me"
}
```

Codes include:

- `VALIDATION_ERROR`
- `USERNAME_TAKEN`
- `EMAIL_TAKEN`
- `INVALID_CREDENTIALS`
- `UNAUTHORIZED`
- `FORBIDDEN`
- `GAME_NOT_FOUND`
- `INVALID_GAME_ACTION`
- `SEAT_UNAVAILABLE`
- `GAME_ALREADY_STARTED`
- `NOT_PLAYER_TURN`
- `INSUFFICIENT_CHIPS`
- `INVALID_RAISE`

## WebSocket (STOMP)

Handshake: `ws://localhost:8080/ws` (HTTP upgrade is public). Authenticate on **STOMP CONNECT** with `Authorization: Bearer <token>`, or pass `?access_token=` on the handshake URL.

| Direction | Destination | Purpose |
| --- | --- | --- |
| Client subscribe | `/user/queue/game/{tableId}` | Sanitized game events for this player |
| Client subscribe | `/user/queue/errors` | Action / validation errors |
| Client send | `/app/game/{tableId}/action` | Fold, check, call, bet, raise, all-in |

Action body:

```json
{
  "action": "FOLD",
  "amount": 0,
  "requestId": "fold-1"
}
```

`requestId` is optional. The same id for the same player at the same table is applied once.

Event `type` values include `GAME_STATE`, `PLAYER_JOINED`, `PLAYER_LEFT`, `HAND_STARTED`, `PLAYER_ACTION`, `COMMUNITY_CARDS`, `SHOWDOWN`, `HAND_COMPLETED`, `PLAYER_DISCONNECTED`, `PLAYER_RECONNECTED`, `ERROR`.

A new subscribe to `/user/queue/game/{tableId}` is answered with `GAME_STATE` for that viewer. Disconnect never folds. The first sit is not a reconnect; `PLAYER_RECONNECTED` is only broadcast when `connected` goes from false to true.

Each event that carries `state` is sanitized for that viewer: your hole cards are real codes; opponents are `HIDDEN` until showdown. The deck is never sent.

## OpenAPI

- Docs JSON: `GET /v3/api-docs`
- Swagger UI: http://localhost:8080/swagger-ui.html

Use the Authorize button with a Bearer token for `/api/auth/me` and `/api/tables`. WebSocket CONNECT uses the same JWT.
