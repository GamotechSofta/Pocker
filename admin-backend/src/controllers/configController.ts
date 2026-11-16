import { Request, Response } from 'express';
import { AuditLog } from '../models/AuditLog';
import { AdminAuthRequest } from '../middleware/authMiddleware';

// In-memory / MongoDB config store for dynamic game parameters
const DEFAULT_CONFIGS: Record<string, any> = {
  TEXAS_HOLDEM: {
    variantName: "No-Limit Texas Hold'em",
    smallBlind: 10,
    bigBlind: 20,
    minBuyIn: 200,
    maxBuyIn: 2000,
    turnTimeoutSeconds: 20,
    timeBankSeconds: 15,
    rakePercentage: 5.0,
    rakeCap: 50,
    maxPlayers: 6,
    enabledStakeTiers: [10, 20, 50, 100, 250, 500],
  },
  OMAHA: {
    variantName: 'Pot-Limit Omaha (PLO)',
    smallBlind: 10,
    bigBlind: 20,
    minBuyIn: 200,
    maxBuyIn: 2000,
    turnTimeoutSeconds: 20,
    timeBankSeconds: 15,
    rakePercentage: 5.0,
    rakeCap: 50,
    maxPlayers: 6,
    enabledStakeTiers: [10, 20, 50, 100, 250, 500],
  },
  PINEAPPLE: {
    variantName: 'Pineapple / Crazy Pineapple',
    smallBlind: 10,
    bigBlind: 20,
    minBuyIn: 200,
    maxBuyIn: 2000,
    turnTimeoutSeconds: 20,
    timeBankSeconds: 15,
    rakePercentage: 5.0,
    rakeCap: 50,
    maxPlayers: 6,
    enabledStakeTiers: [10, 20, 50, 100, 250, 500],
  },
  FIVE_CARD_DRAW: {
    variantName: '5-Card Draw',
    smallBlind: 10,
    bigBlind: 20,
    minBuyIn: 200,
    maxBuyIn: 2000,
    turnTimeoutSeconds: 20,
    timeBankSeconds: 15,
    rakePercentage: 5.0,
    rakeCap: 50,
    maxPlayers: 6,
    enabledStakeTiers: [10, 20, 50, 100, 250, 500],
  },
  RAZZ: {
    variantName: '7-Card Stud Razz',
    smallBlind: 10,
    bigBlind: 20,
    ante: 2,
    minBuyIn: 200,
    maxBuyIn: 2000,
    turnTimeoutSeconds: 20,
    timeBankSeconds: 15,
    rakePercentage: 5.0,
    rakeCap: 50,
    maxPlayers: 6,
    enabledStakeTiers: [10, 20, 50, 100, 250, 500],
  },
  // Legacy / Rummy aliases for cross-compatibility
  POINTS_13: {
    variantName: 'Points Rummy (13 Cards)',
    cardsPerPlayer: 13,
    turnTimeoutSeconds: 15,
    extraTimeSeconds: 10,
    declareTimeoutSeconds: 30,
    firstDropPenalty: 20,
    middleDropPenalty: 40,
    wrongDeclarePenalty: 80,
    rakePercentage: 10.0,
    enabledStakeTiers: [0.05, 0.1, 0.25, 0.5, 1, 2, 5, 10, 25, 50],
  },
  DEALS_2: {
    variantName: 'Deals Rummy (Best of 2)',
    totalDeals: 2,
    cardsPerPlayer: 13,
    turnTimeoutSeconds: 15,
    declareTimeoutSeconds: 30,
    interDealCountdownSeconds: 5,
    firstDropPenalty: 20,
    middleDropPenalty: 40,
    wrongDeclarePenalty: 80,
    rakePercentage: 10.0,
  },
  POOL_101: {
    variantName: 'Pool Rummy (101 Pool)',
    eliminationThreshold: 101,
    cardsPerPlayer: 13,
    turnTimeoutSeconds: 15,
    firstDropPenalty: 20,
    middleDropPenalty: 40,
    wrongDeclarePenalty: 80,
    rejoinAllowed: true,
    rejoinMaxScore: 79,
    rakePercentage: 10.0,
  },
};

const liveOverrides: Record<string, any> = {};

export async function getRulesetConfigs(req: Request, res: Response): Promise<void> {
  const merged: Record<string, any> = {};
  for (const [key, val] of Object.entries(DEFAULT_CONFIGS)) {
    merged[key] = { ...val, ...(liveOverrides[key] || {}) };
  }
  res.json({ success: true, configs: merged });
}

export async function updateRulesetConfig(req: AdminAuthRequest, res: Response): Promise<void> {
  try {
    const { variantId } = req.params;
    const vId = String(variantId || '').toUpperCase();
    const updates = req.body;

    if (!DEFAULT_CONFIGS[vId]) {
      res.status(404).json({ success: false, message: `Unknown variant ruleset: ${variantId}` });
      return;
    }

    liveOverrides[vId] = { ...(liveOverrides[vId] || {}), ...updates };

    // Record audit log
    await AuditLog.create({
      action: 'UPDATE_RULESET_CONFIG',
      performedBy: req.adminUser?.username || 'SYSTEM',
      target: vId,
      details: { updates },
    });

    res.json({
      success: true,
      message: `Updated configuration for ${vId}`,
      config: { ...DEFAULT_CONFIGS[vId], ...liveOverrides[vId] },
    });
  } catch (err: any) {
    res.status(500).json({ success: false, message: err.message });
  }
}
