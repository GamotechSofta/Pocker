import mongoose, { Schema } from 'mongoose';

// 1. games collection
const GameSchema = new Schema(
  {
    _id: { type: String },
    gameId: { type: String, index: true },
    tableId: { type: String, index: true },
    rulesetId: { type: String, index: true }, // TEXAS_HOLDEM, OMAHA, PINEAPPLE, FIVE_CARD_DRAW, RAZZ
    status: { type: String, index: true }, // IN_PROGRESS, COMPLETED, ABORTED
    startedAt: { type: Date, index: true },
    finishedAt: { type: Date },
    winnerPlayerId: { type: String },
    communityCards: { type: [String], default: [] },
    winningHand: { type: String }, // e.g. "Full House, Aces full of Kings"
    pot: { type: Number, default: 0 },
    potSize: { type: Number, default: 0 },
    rake: { type: Number, default: 0 },
    smallBlind: { type: Number, default: 10 },
    bigBlind: { type: Number, default: 20 },
    players: { type: Array, default: [] },
    totalTurns: { type: Number, default: 0 },
    durationSeconds: { type: Number, default: 0 },
  },
  { collection: 'games', timestamps: false, strict: false }
);

export const Game = mongoose.model('Game', GameSchema);

// 2. game_results collection
const GameResultSchema = new Schema(
  {
    gameId: { type: String, index: true },
    tableId: { type: String },
    playerId: { type: String, index: true },
    displayName: { type: String },
    finalScore: { type: Number, default: 0 },
    netChips: { type: Number, default: 0 },
    won: { type: Boolean, default: false },
    handRank: { type: String },
    handCards: { type: [String], default: [] },
    status: { type: String }, // WON, LOST, FOLDED
    createdAt: { type: Date, default: Date.now, index: true },
  },
  { collection: 'game_results', timestamps: false, strict: false }
);

export const GameResult = mongoose.model('GameResult', GameResultSchema);

// 3. game_events collection (immutable event stream)
const GameEventSchema = new Schema(
  {
    gameId: { type: String, index: true },
    sequence: { type: Number, index: true },
    eventType: { type: String, index: true }, // DEAL, BLINDS, ACTION (FOLD/CHECK/CALL/BET/RAISE), FLOP, TURN, RIVER, SHOWDOWN, WINNER
    playerId: { type: String },
    payloadJson: { type: String },
    timestamp: { type: Date, default: Date.now },
  },
  { collection: 'game_events', timestamps: false, strict: false }
);

export const GameEvent = mongoose.model('GameEvent', GameEventSchema);

// 4. wallet_transactions collection
const WalletTransactionSchema = new Schema(
  {
    idempotencyKey: { type: String, unique: true },
    playerId: { type: String, index: true },
    gameId: { type: String, index: true },
    transactionType: { type: String, index: true }, // BUY_IN, WIN_CREDIT, RAKE, REFUND
    amount: { type: Number, default: 0 },
    balanceBefore: { type: Number, default: 0 },
    balanceAfter: { type: Number, default: 0 },
    status: { type: String, default: 'SUCCESS' },
    currency: { type: String, default: 'INR' },
    description: { type: String },
    createdAt: { type: Date, default: Date.now, index: true },
    metadata: { type: Schema.Types.Mixed },
  },
  { collection: 'wallet_transactions', timestamps: false, strict: false }
);

export const WalletTransaction = mongoose.model('WalletTransaction', WalletTransactionSchema);

// 5. poker_tables collection (for direct live table inspection if populated)
const PokerTableSchema = new Schema(
  {
    _id: { type: String },
    name: { type: String },
    variant: { type: String, index: true }, // TEXAS_HOLDEM, OMAHA, etc.
    status: { type: String, index: true }, // WAITING, IN_PROGRESS, FINISHED
    smallBlind: { type: Number, default: 10 },
    bigBlind: { type: Number, default: 20 },
    maxPlayers: { type: Number, default: 6 },
    currentPot: { type: Number, default: 0 },
    players: { type: Array, default: [] },
    createdAt: { type: Date, default: Date.now },
    updatedAt: { type: Date, default: Date.now },
  },
  { collection: 'poker_tables', timestamps: true, strict: false }
);

export const PokerTable = mongoose.model('PokerTable', PokerTableSchema);
