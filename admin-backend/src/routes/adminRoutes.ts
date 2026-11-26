import { Router } from 'express';
import { login, getProfile } from '../controllers/authController';
import { getOverviewMetrics, toggleNodeDrain } from '../controllers/overviewController';
import { getVariantMetrics } from '../controllers/variantController';
import { getLiveTables, terminateTable } from '../controllers/tableController';
import { getGameReplay } from '../controllers/replayController';
import { getGameDetails } from '../controllers/gameDetailController';
import { getRulesetConfigs, updateRulesetConfig } from '../controllers/configController';
import { authMiddleware } from '../middleware/authMiddleware';
import { requireRole } from '../middleware/rbacMiddleware';
import {
  cancelTournament,
  createTournament,
  getTournament,
  listEntrants,
  listResults,
  listTables,
  listTournaments,
  publishTournament,
  tournamentClock,
  tournamentLeaderboard,
  updateTournament,
} from '../controllers/tournamentController';

const router = Router();
const loginAttempts = new Map<string, number>();
const loginWindowMs = 60_000;
setInterval(() => loginAttempts.clear(), loginWindowMs).unref();

// Public auth routes
router.post('/auth/login', (req, res, next) => {
  const key = req.ip ?? 'unknown';
  const attempts = loginAttempts.get(key) ?? 0;
  if (attempts >= 20 || (!loginAttempts.has(key) && loginAttempts.size >= 10_000)) {
    res.setHeader('Retry-After', String(loginWindowMs / 1000));
    res.status(429).json({ success: false, message: 'Too many login attempts. Try again later.' });
    return;
  }
  loginAttempts.set(key, attempts + 1);
  next();
}, login);

// Protected routes (require JWT)
router.get('/auth/me', authMiddleware, getProfile);

// Overview / Main Dashboard
router.get('/overview/metrics', authMiddleware, getOverviewMetrics);
router.post('/overview/drain', authMiddleware, requireRole('SUPER_ADMIN'), toggleNodeDrain);

// Variant-specific dashboards
router.get('/variants/:variantId/metrics', authMiddleware, getVariantMetrics);

// Live Tables Spectator
router.get('/tables/live', authMiddleware, getLiveTables);
router.post('/tables/:tableId/terminate', authMiddleware, requireRole('SUPER_ADMIN', 'GAME_OPERATOR'), terminateTable);

// Game Details & Dispute Replay
router.get('/games/:gameId', authMiddleware, getGameDetails);
router.get('/replay/:gameId', authMiddleware, getGameReplay);

// Rules & Rake configuration
router.get('/config/rulesets', authMiddleware, getRulesetConfigs);
router.put('/config/rulesets/:variantId', authMiddleware, requireRole('SUPER_ADMIN'), updateRulesetConfig);

// Tournament management. Gameplay start/orchestration is intentionally not exposed here.
router.post('/tournaments', authMiddleware, requireRole('SUPER_ADMIN', 'GAME_OPERATOR'), createTournament);
router.get('/tournaments', authMiddleware, listTournaments);
router.get('/tournaments/:id', authMiddleware, getTournament);
router.patch('/tournaments/:id', authMiddleware, requireRole('SUPER_ADMIN', 'GAME_OPERATOR'), updateTournament);
router.post('/tournaments/:id/publish', authMiddleware, requireRole('SUPER_ADMIN', 'GAME_OPERATOR'), publishTournament);
router.post('/tournaments/:id/cancel', authMiddleware, requireRole('SUPER_ADMIN', 'GAME_OPERATOR'), cancelTournament);
router.get('/tournaments/:id/entrants', authMiddleware, listEntrants);
router.get('/tournaments/:id/leaderboard', authMiddleware, tournamentLeaderboard);
router.get('/tournaments/:id/clock', authMiddleware, tournamentClock);
router.post('/tournaments/:id/clock/:operation', authMiddleware, requireRole('SUPER_ADMIN', 'GAME_OPERATOR'), tournamentClock);
router.get('/tournaments/:id/tables', authMiddleware, listTables);
router.get('/tournaments/:id/results', authMiddleware, listResults);

export default router;
