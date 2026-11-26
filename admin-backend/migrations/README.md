# Tournament index migration 001

This is a standalone migration for the six tournament collections. The project has no shared migration runner. Run it from the repository root with the admin backend dependencies installed. Use a URI for the intended MongoDB deployment and pass the database name explicitly.

```powershell
$env:TOURNAMENT_INDEX_MONGO_URI = '<deployment URI>'
node admin-backend/migrations/001-tournament-indexes.cjs --dry-run '<database>'
node admin-backend/migrations/001-tournament-indexes.cjs --apply '<database>'
```

Dry-run reads collection metadata and scans the keys of the proposed unique indexes. It reports the desired ordered key, options, current conflicts, and up to ten duplicate key groups or unexpected array-valued documents per unique index. It also lists unmanaged indexes for review. Treat the output as database data and store it with the backup. Exit code 0 means the plan is clear, 2 means it is blocked, and 1 means the utility encountered an error. Large collections may require a maintenance window even for inspection.

`--apply` performs a new preflight and creates only missing indexes on an unblocked plan. A repeat run with all indexes present makes no changes. The utility has no drop, rename, document update, or deletion commands. MongoDB enforces uniqueness during index creation if data changes after the preflight. If a build fails after some indexes were created, inspect the resulting state before retrying.

## Current index ownership

| Collection | Shared by Java and Express | Additional indexes |
| --- | --- | --- |
| `tournaments` | Sparse unique `privateInviteCode_1` and `creatorId_1` | Express manages `status_1`, `status_1_scheduledStartAt_1`, and `status_1_createdAt_-1`. |
| `tournament_blind_schedules` | Unique `tournamentId_1` | |
| `tournament_entrants` | `tournamentId_1`, `userId_1`, unique `tournamentId_1_userId_1`, and partial unique `tournament_finished_rank_unique` | Express manages `status_1` and sparse `finishPosition_1`. |
| `tournament_table_assignments` | `tournamentId_1`, `entrantId_1`, `tableId_1`, unique `tournamentId_1_entrantId_1`, and unique `tournamentId_1_tableId_1_seat_1` | |
| `tournament_final_results` | `tournamentId_1`, `entrantId_1`, unique `tournamentId_1_entrantId_1`, and unique `tournamentId_1_finishPosition_1` | |
| `tournament_chip_ledger` | | Java manages `tournamentId`, `entrantId`, and unique `chip_ledger_idempotency_unique`. |

MongoDB manages each collection's unique `_id_` index. The partial rank index covers rows with `finishPosition >= 1`. Earlier compound sparse indexes such as `tournament_rank_unique` and `tournamentId_1_finishPosition_1` include unranked rows because `tournamentId` is present, so they reject a second unranked entrant in the same tournament. Common legacy Java names include `privateInviteCode`, `tournamentId`, and `tournament_user_unique`.

## Backup, rollout, and rollback

1. Verify the target host and database. Pause writers and stop both service versions during schema reconciliation. Save the complete dry-run report, index definitions, and a consistent full database backup. For a replica set or sharded deployment, follow its approved snapshot or consistent-backup procedure. Confirm the backup can be restored to a separate isolated database. Record counts and index definitions before proceeding.
2. Review every conflict and duplicate with the data owner. Resolve duplicates by an explicitly approved correction that preserves required records. Keep unrelated indexes. Obtain explicit, target-specific approval for any destructive change, including dropping a legacy index.
3. Prefer a replacement database for environments with unresolved legacy definitions. Restore or copy all data to the replacement with matching collection options and unrelated indexes. For the six tournament collections, preserve compatible and unmanaged indexes and omit only the reviewed conflicts when constructing the replacement. Keep the original database intact. Run dry-run and apply on the replacement, then rerun both and verify counts, index definitions, and real Express-to-Java reads before cutover.
4. If operations approves an in-place migration, have the DBA execute only the approved collection/index removals in the maintenance window. Re-run dry-run before applying. Verify that all checks are `present`, both backends start, and lifecycle and wallet smoke checks pass before reopening writes.
5. If a replacement fails verification before writes resume, switch both services back to the original database and previous app version. For an in-place rollback, stop writers, restore the recorded index definitions or restore the backup into a separate database, verify it, and switch both services together. If writes resumed after cutover, reconcile them under the deployment's recovery procedure before switching back.
