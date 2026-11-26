# Local MongoDB for tournament development

Registration and preparation require MongoDB transactions. The default application Compose file remains unchanged. The separate development file starts a single-node MongoDB 7 replica set on loopback with its own named volume.

```powershell
docker compose -p poker-tournaments-local -f docker-compose.tournaments.yml up -d --wait
docker compose -p poker-tournaments-local -f docker-compose.tournaments.yml ps
$env:MONGODB_URI = 'mongodb://127.0.0.1:27019/poker_tournament_local?replicaSet=poker-local'
```

Run the Java and Express applications from their usual project directories with the same `MONGODB_URI`. The health check initializes the replica set if needed and reports healthy only when its member is writable. The advertised host is loopback, so run both applications on the host with this file. In an equivalent native environment, start `mongod` with `--replSet poker-local --bind_ip 127.0.0.1 --port 27019`, initialize a member at `127.0.0.1:27019`, and wait for the writable primary before starting the apps.

For the Java integration suite, set `POKER_TEST_MONGO_URI=mongodb://127.0.0.1:27019` so it can create a disposable database. The cross-backend compatibility test also requires its prior Express fixture URI and JSON file. Keep the local instance and these fixtures separate from application databases containing user data.

The scheduler is enabled by default and advances recoverable work approximately every 2.5 seconds. The `app.tournament.poll-ms` setting controls its cadence. Setting `app.tournament.auto-start-enabled=false` disables both the scanner and the final-seat automatic close. The test profile disables background automation for deterministic regression fixtures. New integration tests drive the same lifecycle service directly with a controlled clock.

The separate gameplay worker activates prepared tournaments, applies expired turns, and deals the next hand approximately every 500 ms. Set `app.tournament.gameplay-enabled=false` while staging rollout against prepared data. This setting stops the background worker, so it also pauses automatic turn enforcement and next-hand creation. `app.tournament.gameplay-poll-ms` controls the worker delay. Every game backend follows committed tournament revisions for its local STOMP subscribers. `app.tournament.broadcast-poll-ms` controls that check, which defaults to 250 ms.

Treat `poker_tables.gameplay` as server-only data. Its current-hand journal contains the actual shuffled deck, even for cards yet to be dealt. Limit database and backup access accordingly. Deploy compatible journal readers across all game backends and keep an active-hand journal compatible when changing the engine. A journal that cannot be replayed is preserved for recovery review and pauses the tournament. Its last settled stacks remain in MongoDB.

The gameplay worker also advances the clock from persisted UTC level boundaries. A running clock counts service downtime. An administratively paused clock preserves its remaining time during downtime. The current hand records its applied level in journal format 2, while the existing format 1 still replays using the table's original blinds. Remove earlier readers from all game traffic before enabling format 2 writers. Clock state is stored on the existing tournament document and requires no index migration.

Set the same `TOURNAMENT_CLOCK_ADMIN_KEY` of at least 32 characters in both local backends to enable admin clock monitoring and operations. Express checks the admin identity and role before forwarding pause or resume to Java. The game server also checks the service credential. The schedule currently stores break markers without durations, so timed breaks are unavailable.

Before any production rollout, confirm both backends connect to the same transaction-capable replica set or sharded cluster with standard BSON UUIDs and the canonical tournament indexes. Existing index reconciliation uses the reviewed process in the [migration runbook](../admin-backend/migrations/README.md). The dedicated Compose file is for local development and requires a separate production security and availability configuration.
