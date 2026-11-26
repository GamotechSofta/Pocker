const { index, inspect, main } = require('./001-tournament-indexes.cjs');

const migration = '002-tournament-runtime-indexes';
const indexes = {
  tournaments: [
    index({ visibility: 1, status: 1, scheduledStartAt: 1, _id: 1 }, { name: 'tournament_public_scheduled' }),
    index({ status: 1, 'blindClock.levelEndsAt': 1 }, { name: 'tournament_clock_due' }),
    index({ status: 1, 'initialization.phase': 1 }, { name: 'tournament_activation_due' }),
    index({ status: 1, 'topology.pending.stage': 1 }, { name: 'tournament_transfer_due' }),
    index({ status: 1, 'finalization.stage': 1, 'finalization.readyAt': 1 }, { name: 'tournament_finalization_due' }),
  ],
  tournament_entrants: [
    index({ tournamentId: 1, registeredAt: 1, _id: 1 }, { name: 'tournament_entrant_registration_order' }),
  ],
  tournament_final_results: [
    index({ userId: 1, completedAt: -1, _id: 1 }, { name: 'tournament_player_result_history' }),
  ],
  tournament_settled_hands: [
    index({ tournamentId: 1, tableId: 1, 'hand.number': 1 }, { name: 'tournament_table_hand_unique', unique: true }),
  ],
  poker_tables: [
    index({ tournamentId: 1, gameMode: 1, tableNumber: 1 }, { name: 'tournament_tables_by_event' }),
    index({ gameMode: 1, 'gameplay.phase': 1, 'gameplay.nextHandAt': 1 }, { name: 'tournament_tables_due' }),
    index({ gameMode: 1, 'gameplay.phase': 1, 'gameplay.turnDeadline': 1 }, { name: 'tournament_table_turn_due' }),
  ],
};

if (require.main === module) {
  main(indexes, migration).catch(error => {
    console.error('Migration stopped:', error.codeName ?? error.name);
    process.exitCode = 1;
  });
}

module.exports = { indexes, inspect: db => inspect(db, indexes, migration) };
