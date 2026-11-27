# Royal Poker Live Environment

Send this template to the deployment manager. Replace every `<...>` value with the real production value. Do not commit the completed file or send secrets in a public chat.

## 1. Docker Compose environment file

Save as `.env.live` beside `docker-compose.yml` on the deployment host.

```dotenv
# MongoDB identity
MONGO_ROOT_USERNAME=<strong-mongo-root-username>
MONGO_APP_USERNAME=<strong-mongo-runtime-username>
MONGODB_DB=poker_live
MONGODB_URI=mongodb://<encoded-runtime-username>:<encoded-runtime-password>@mongo:27017/poker_live?replicaSet=poker-staging&authSource=poker_live&maxPoolSize=40&serverSelectionTimeoutMS=5000
MONGO_ROOT_PASSWORD_FILE=.secrets/mongo-root-password
MONGO_APP_PASSWORD_FILE=.secrets/mongo-app-password
MONGO_KEYFILE_FILE=.secrets/mongo-keyfile
MONGODB_AUTO_INDEX_CREATION=false

# Application secrets: use separate random values, minimum 32 bytes
JWT_SECRET=<random-player-jwt-secret-32-plus-bytes>
ADMIN_JWT_SECRET=<different-random-admin-jwt-secret-32-plus-bytes>
ADMIN_API_KEY=<different-random-admin-operations-key-32-plus-bytes>
TOURNAMENT_CLOCK_ADMIN_KEY=<different-random-tournament-clock-key-32-plus-bytes>

# One-time admin bootstrap
ADMIN_BOOTSTRAP_USERNAME=<initial-admin-username>
ADMIN_BOOTSTRAP_PASSWORD=<strong-initial-admin-password>

# Public exact origins. Add all five player domains, comma-separated.
PLAYER_ORIGIN=https://holdem.<production-domain>,https://omaha.<production-domain>,https://pineapple.<production-domain>,https://fivecard.<production-domain>,https://razz.<production-domain>
ADMIN_ORIGIN=https://admin.<production-domain>

# Internal reverse-proxy ports; keep bound to 127.0.0.1 in Docker Compose.
PLAYER_PROXY_PORT=15775
HOLDEM_PROXY_PORT=15776
OMAHA_PROXY_PORT=15777
PINEAPPLE_PROXY_PORT=15778
FIVE_CARD_PROXY_PORT=15779
RAZZ_PROXY_PORT=15780
ADMIN_PROXY_PORT=15680

# Optional runtime node label for logs/operations
POKER_RUNTIME_NODE=live-game-1
```

The MongoDB password used in `MONGODB_URI` must be URL encoded. Keep the root password, app password, and keyfile as separate files under `.secrets/` with restrictive permissions.

## 2. Docker secret files

Create these files on the host. Their contents are secret values, without quotes:

```text
.secrets/mongo-root-password
.secrets/mongo-app-password
.secrets/mongo-keyfile
```

The keyfile must be a long random value accepted by MongoDB replica-set authentication. Do not use the example/local values.

## 3. Frontend build variables

The player frontend uses same-origin `/api` and `/ws` through Nginx, so production API URLs remain blank. Each variant is built from its own folder and already has its game lock:

| Build | `VITE_POKER_VARIANT` | `VITE_APP_TITLE` | Public port |
|---|---|---|---:|
| `frontend-holdem` | `TEXAS_HOLDEM` | `Royal Texas Hold'em` | `15776` |
| `frontend-omaha` | `OMAHA` | `Royal Omaha Poker` | `15777` |
| `frontend-pineapple` | `PINEAPPLE` | `Royal Pineapple Poker` | `15778` |
| `frontend-five-card` | `FIVE_CARD_DRAW` | `Royal 5-Card Draw` | `15779` |
| `frontend-razz` | `RAZZ` | `Royal Razz Poker` | `15780` |

For each production frontend build:

```dotenv
VITE_API_URL=
VITE_WS_URL=
```

For the admin frontend production build, leave `VITE_ADMIN_API_URL` blank so it uses `/api/admin` through the same origin.

## 4. Reverse proxy / DNS

Route the public HTTPS domains to the matching localhost ports on the deployment host:

```text
holdem.<production-domain>    -> 127.0.0.1:15776
omaha.<production-domain>     -> 127.0.0.1:15777
pineapple.<production-domain> -> 127.0.0.1:15778
fivecard.<production-domain>  -> 127.0.0.1:15779
razz.<production-domain>      -> 127.0.0.1:15780
admin.<production-domain>     -> 127.0.0.1:15680
```

The proxy must support WebSocket upgrade for `/ws` and forward HTTPS traffic only. The exact public origins above must match the browser origins; do not add paths or trailing slashes.

## 5. Deploy command

From the repository root on the live host:

```powershell
docker compose --env-file .env.live up -d --build
docker compose --env-file .env.live ps
docker compose --env-file .env.live logs -f backend admin-backend
```

Verify the player health endpoint through the backend host and verify `/health/ready` for the admin backend before opening the public domains.
