import { Request, Response } from 'express';
import { Game, GameResult, WalletTransaction } from '../models/GameModels';

export async function getVariantMetrics(req: Request, res: Response): Promise<void> {
  try {
    const { variantId } = req.params;
    const vId = String(variantId || '').toUpperCase();

    // Query filters
    const filterMatchType = String(req.query.matchType || 'ALL').toUpperCase(); // ALL | REAL_VS_REAL | REAL_VS_BOT
    const filterPlayerCount = String(req.query.playerCount || 'ALL').toUpperCase(); // ALL | 2 | 6
    const limit = Math.min(100, Math.max(1, parseInt(String(req.query.limit || '50'), 10) || 50));

    // Regex match to catch Poker variants (and Rummy aliases for backward compatibility)
    let filterRegex: RegExp;
    if (vId.includes('HOLDEM') || vId.includes('TEXAS')) {
      filterRegex = /HOLDEM|TEXAS/i;
    } else if (vId.includes('OMAHA')) {
      filterRegex = /OMAHA/i;
    } else if (vId.includes('PINEAPPLE')) {
      filterRegex = /PINEAPPLE/i;
    } else if (vId.includes('DRAW') || vId.includes('FIVE_CARD')) {
      filterRegex = /DRAW|FIVE_CARD/i;
    } else if (vId.includes('RAZZ')) {
      filterRegex = /RAZZ/i;
    } else if (vId.includes('POINT')) {
      filterRegex = /POINT|HOLDEM|TEXAS/i;
    } else if (vId.includes('DEAL')) {
      filterRegex = /DEAL|OMAHA/i;
    } else if (vId.includes('POOL') || vId.includes('101') || vId.includes('201')) {
      filterRegex = /POOL|PINEAPPLE/i;
    } else if (vId.includes('21')) {
      filterRegex = /21|DRAW|RAZZ/i;
    } else {
      filterRegex = new RegExp(vId, 'i');
    }

    const now = new Date();
    const istOffsetMs = 5.5 * 60 * 60 * 1000;
    const istNow = new Date(now.getTime() + istOffsetMs);
    const todayStart = new Date(
      Date.UTC(istNow.getUTCFullYear(), istNow.getUTCMonth(), istNow.getUTCDate(), 0, 0, 0, 0) - istOffsetMs
    );
    const past24h = new Date(Date.now() - 24 * 60 * 60 * 1000);

    // 1. Matches played
    const [totalToday, total24h, totalAllTime, allVariantGames] = await Promise.all([
      Game.countDocuments({ rulesetId: filterRegex, startedAt: { $gte: todayStart } }),
      Game.countDocuments({ rulesetId: filterRegex, startedAt: { $gte: past24h } }),
      Game.countDocuments({ rulesetId: filterRegex }),
      Game.find({ rulesetId: filterRegex }).sort({ startedAt: -1 }).lean(),
    ]);

    // 2. Average game duration in seconds
    const durationAgg = await Game.aggregate([
      { $match: { rulesetId: filterRegex, durationSeconds: { $gt: 0 } } },
      { $group: { _id: null, avgDuration: { $avg: '$durationSeconds' }, maxDuration: { $max: '$durationSeconds' } } },
    ]);
    const avgDuration = durationAgg.length > 0 ? Math.round(durationAgg[0].avgDuration) : 120;

    // 3. Outcomes Breakdown (Won, Folded, Lost)
    const outcomesAgg = await GameResult.aggregate([
      {
        $lookup: {
          from: 'games',
          localField: 'gameId',
          foreignField: 'gameId',
          as: 'game',
        },
      },
      { $unwind: '$game' },
      { $match: { 'game.rulesetId': filterRegex } },
      {
        $group: {
          _id: '$status',
          count: { $sum: 1 },
          avgScore: { $avg: '$finalScore' },
        },
      },
    ]);

    const outcomeBreakdown = {
      WON: 0,
      DROPPED: 0, // Maps to FOLDED in poker
      LOST: 0,
      WRONG_DECLARED: 0,
    };

    outcomesAgg.forEach((o) => {
      const s = (o._id || '').toUpperCase();
      if (s === 'WON') outcomeBreakdown.WON += o.count;
      else if (s === 'DROPPED' || s === 'FOLD' || s === 'FOLDED') outcomeBreakdown.DROPPED += o.count;
      else if (s.includes('WRONG')) outcomeBreakdown.WRONG_DECLARED += o.count;
      else outcomeBreakdown.LOST += o.count;
    });

    // 4. Fetch ledger transactions for this variant's games
    const gameIds = allVariantGames.map((g: any) => g.gameId || g._id);
    const txs = await WalletTransaction.find({
      gameId: { $in: gameIds },
      transactionType: { $in: ['PLATFORM_RAKE', 'BOT_HOUSE_WIN', 'BOT_HOUSE_LOSS'] },
    }).lean();

    const txMap = new Map<string, { rake: number; botWin: number; botLoss: number; count: number }>();
    for (const tx of txs) {
      if (!tx.gameId) continue;
      const existing = txMap.get(tx.gameId) || { rake: 0, botWin: 0, botLoss: 0, count: 0 };
      const amt = parseFloat(String(tx.amount || 0)) || 0;
      if (tx.transactionType === 'PLATFORM_RAKE') existing.rake += amt;
      else if (tx.transactionType === 'BOT_HOUSE_WIN') existing.botWin += amt;
      else if (tx.transactionType === 'BOT_HOUSE_LOSS') existing.botLoss += amt;
      existing.count++;
      txMap.set(tx.gameId, existing);
    }

    // 5. Compute P&L and classify each match
    let totalRvrProfit = 0;
    let totalRvbProfit = 0;
    let rvrMatchesCount = 0;
    let rvbMatchesCount = 0;
    let twoPlayerMatchesCount = 0;
    let sixPlayerMatchesCount = 0;

    const enrichedMatches = allVariantGames.map((g: any) => {
      const gid = g.gameId || g._id;
      const players: any[] = g.players || [];
      const hasBot = players.some((p: any) => p.isBot === true || String(p.playerId || '').startsWith('BOT_'));
      const matchType: 'REAL_VS_REAL' | 'REAL_VS_BOT' = hasBot ? 'REAL_VS_BOT' : 'REAL_VS_REAL';
      
      const rawCount = players.length;
      const playerCount = rawCount >= 5 ? 6 : (rawCount <= 2 ? 2 : rawCount);

      if (matchType === 'REAL_VS_REAL') {
        rvrMatchesCount++;
      } else {
        rvbMatchesCount++;
      }

      if (playerCount === 2) {
        twoPlayerMatchesCount++;
      } else if (playerCount === 6) {
        sixPlayerMatchesCount++;
      }

      const isBotWinner = String(g.winnerPlayerId || '').startsWith('BOT_') ||
        players.some((p: any) => (p.isBot || String(p.playerId || '').startsWith('BOT_')) && (p.won || p.playerId === g.winnerPlayerId));

      let gameProfit = 0;
      if (txMap.has(gid)) {
        const t = txMap.get(gid)!;
        if (matchType === 'REAL_VS_REAL') {
          gameProfit = t.rake;
        } else {
          gameProfit = t.botWin - t.botLoss + t.rake;
        }
      } else {
        // Fallback calculation from game's pot/rake
        if (g.rake && g.rake > 0) {
          gameProfit = g.rake;
        } else if (g.pot && g.pot > 0) {
          gameProfit = Math.round(g.pot * 0.05 * 100) / 100; // 5% rake default
        } else if (matchType === 'REAL_VS_BOT') {
          gameProfit = isBotWinner ? 30.0 : -30.0;
        } else {
          gameProfit = 15.0; // default real vs real rake
        }
      }

      if (matchType === 'REAL_VS_REAL') {
        totalRvrProfit += gameProfit;
      } else {
        totalRvbProfit += gameProfit;
      }

      return {
        _id: gid,
        gameId: gid,
        tableId: g.tableId || 'N/A',
        rulesetId: g.rulesetId,
        status: g.status || 'COMPLETED',
        startedAt: g.startedAt,
        finishedAt: g.finishedAt,
        durationSeconds: g.durationSeconds || 0,
        winnerPlayerId: g.winnerPlayerId || 'N/A',
        winnerIsBot: isBotWinner,
        winningHand: g.winningHand || '',
        pot: g.pot || g.potSize || 0,
        rake: g.rake || 0,
        communityCards: g.communityCards || [],
        matchType,
        playerCount,
        gameProfit: Math.round(gameProfit * 100) / 100,
        players,
      };
    });

    const totalProfit = totalRvrProfit + totalRvbProfit;

    // 6. Apply Active Filters to Match List
    let filteredMatches = enrichedMatches;

    if (filterMatchType === 'REAL_VS_REAL') {
      filteredMatches = filteredMatches.filter((m) => m.matchType === 'REAL_VS_REAL');
    } else if (filterMatchType === 'REAL_VS_BOT') {
      filteredMatches = filteredMatches.filter((m) => m.matchType === 'REAL_VS_BOT');
    }

    if (filterPlayerCount === '2') {
      filteredMatches = filteredMatches.filter((m) => m.playerCount === 2);
    } else if (filterPlayerCount === '6') {
      filteredMatches = filteredMatches.filter((m) => m.playerCount === 6);
    }

    const recentMatches = filteredMatches.slice(0, limit);

    res.json({
      success: true,
      variantId: vId,
      financials: {
        totalProfit: Math.round(totalProfit * 100) / 100,
        realVsRealProfit: Math.round(totalRvrProfit * 100) / 100,
        realVsBotProfit: Math.round(totalRvbProfit * 100) / 100,
        realVsRealMatchesCount: rvrMatchesCount,
        realVsBotMatchesCount: rvbMatchesCount,
        twoPlayerMatchesCount,
        sixPlayerMatchesCount,
        totalMatchesCount: allVariantGames.length,
      },
      metrics: {
        totalToday,
        total24h,
        totalAllTime,
        avgDurationSeconds: avgDuration,
        outcomeBreakdown,
      },
      activeFilters: {
        matchType: filterMatchType,
        playerCount: filterPlayerCount,
      },
      recentMatches,
    });
  } catch (err: any) {
    res.status(500).json({ success: false, message: err.message });
  }
}
