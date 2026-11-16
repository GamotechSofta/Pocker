import { Request, Response } from 'express';
import mongoose from 'mongoose';
import { Game, PokerTable } from '../models/GameModels';
import { AuditLog } from '../models/AuditLog';
import { AdminAuthRequest } from '../middleware/authMiddleware';

export async function getLiveTables(req: Request, res: Response): Promise<void> {
  try {
    const { variant } = req.query;

    const query: any = {
      status: { $in: ['IN_PROGRESS', 'SHOWDOWN', 'WAITING_FOR_PLAYERS', 'WAITING', 'PLAYING'] },
    };

    if (variant && typeof variant === 'string' && variant !== 'ALL') {
      query.rulesetId = new RegExp(variant, 'i');
    }

    let liveGames = await Game.find(query).sort({ startedAt: -1 }).limit(50).lean();

    // If no games found in 'games', check 'poker_tables'
    if (liveGames.length === 0) {
      const tableQuery: any = {};
      if (variant && typeof variant === 'string' && variant !== 'ALL') {
        tableQuery.variant = new RegExp(variant, 'i');
      }

      const pokerTables = await PokerTable.find(tableQuery).sort({ updatedAt: -1 }).limit(50).lean();
      if (pokerTables.length > 0) {
        liveGames = pokerTables.map((t: any) => {
          const idStr = t._id ? t._id.toString() : String(t.id || '');
          const smallBlind = t.smallBlind || 10;
          const bigBlind = t.bigBlind || 20;
          return {
            _id: idStr,
            gameId: idStr,
            tableId: idStr,
            name: t.name || `${t.variant || 'Poker'} Table`,
            rulesetId: t.variant || 'TEXAS_HOLDEM',
            status: t.status || 'WAITING',
            startedAt: t.createdAt || new Date(),
            players: t.players || [],
            pot: t.currentPot || (smallBlind + bigBlind),
            smallBlind,
            bigBlind,
            startingChips: t.startingChips || 1000,
          };
        }) as any;
      }
    }

    res.json({
      success: true,
      count: liveGames.length,
      tables: liveGames,
    });
  } catch (err: any) {
    res.status(500).json({ success: false, message: err.message });
  }
}

export async function terminateTable(req: AdminAuthRequest, res: Response): Promise<void> {
  try {
    const { tableId } = req.params;
    const { reason } = req.body;

    if (!tableId) {
      res.status(400).json({ success: false, message: 'tableId is required' });
      return;
    }

    let uuidObj: any = null;
    try {
      uuidObj = new mongoose.mongo.BSON.UUID(String(tableId));
    } catch {}

    // Update MongoDB records in both collections
    await Promise.all([
      Game.updateMany(
        { $or: [{ tableId }, { _id: tableId }, { gameId: tableId }] },
        { $set: { status: 'ABORTED', finishedAt: new Date() } }
      ),
      PokerTable.updateMany(
        { $or: [{ _id: tableId as any }, { _id: uuidObj as any }, { id: tableId }] },
        { $set: { status: 'FINISHED' } }
      ),
    ]);

    if (req.adminUser) {
      await AuditLog.create({
        adminId: req.adminUser.id,
        adminUsername: req.adminUser.username,
        action: 'FORCE_TERMINATE_TABLE',
        resource: `TABLE:${tableId}`,
        details: { reason: reason || 'Terminated by administrator' },
      });
    }

    res.json({
      success: true,
      message: `Table ${tableId} has been safely terminated and closed.`,
    });
  } catch (err: any) {
    res.status(500).json({ success: false, message: err.message });
  }
}
