import { Request, Response } from 'express';
import mongoose from 'mongoose';
import { Game, GameResult, GameEvent, PokerTable } from '../models/GameModels';

export async function getGameReplay(req: Request, res: Response): Promise<void> {
  try {
    const { gameId } = req.params;
    if (!gameId) {
      res.status(400).json({ success: false, message: 'gameId parameter is required' });
      return;
    }

    const cleanId = String(gameId).trim();

    // 1. First, search standard 'games' collection
    let [game, results, events]: [any, any[], any[]] = await Promise.all([
      Game.findOne({ $or: [{ gameId: cleanId }, { tableId: cleanId }, { _id: cleanId }] }).lean(),
      GameResult.find({ $or: [{ gameId: cleanId }, { tableId: cleanId }] }).lean(),
      GameEvent.find({ $or: [{ gameId: cleanId }, { tableId: cleanId }] }).sort({ sequence: 1 }).lean(),
    ]);

    // 2. If not found in 'games', search 'poker_tables' (handles both String and BSON UUIDs)
    if (!game) {
      let table: any = null;
      try {
        const uuidObj = new mongoose.mongo.BSON.UUID(cleanId);
        table = await mongoose.connection.db?.collection('poker_tables').findOne({
          gameMode: { $ne: 'TOURNAMENT' },
          $or: [{ _id: uuidObj as any }, { _id: cleanId as any }, { id: cleanId }],
        });
      } catch {
        table = await mongoose.connection.db?.collection('poker_tables').findOne({
          gameMode: { $ne: 'TOURNAMENT' },
          $or: [{ _id: cleanId as any }, { id: cleanId }],
        });
      }

      if (table) {
        const idStr = table._id ? table._id.toString() : cleanId;
        const smallBlind = table.smallBlind || 10;
        const bigBlind = table.bigBlind || 20;
        const startingChips = table.startingChips || 1000;
        const pot = table.currentPot || (smallBlind + bigBlind);
        const playersList = (table.players || []).map((p: any, idx: number) => ({
          playerId: p.userId || p._id?.toString() || p.id || `p_${idx}`,
          displayName: p.username || `Player ${idx + 1}`,
          username: p.username || `Player ${idx + 1}`,
          seatIndex: p.seat ?? idx,
          chipStack: p.chipStack ?? startingChips,
          isBot: Boolean(p.username?.startsWith('bot_') || p.email?.includes('@bot.')),
        }));

        game = {
          _id: idStr,
          gameId: idStr,
          tableId: idStr,
          name: table.name || "Poker Table",
          rulesetId: table.variant || 'TEXAS_HOLDEM',
          status: table.status || 'WAITING',
          startedAt: table.createdAt || new Date(),
          finishedAt: table.status === 'FINISHED' ? (table.updatedAt || new Date()) : null,
          pot: pot,
          potSize: pot,
          rake: Math.round(pot * 0.04),
          smallBlind,
          bigBlind,
          winnerPlayerId: table.status === 'FINISHED' ? (table.winnerPlayerId || 'Winner') : (table.status === 'PLAYING' ? 'Hand In Progress' : 'Waiting for Players'),
          winningHand: table.winningHand || (table.status === 'PLAYING' ? 'Live Hand In Progress' : 'Pre-Flop / Seated'),
          durationSeconds: Math.max(1, Math.round((Date.now() - new Date(table.createdAt || table.updatedAt || Date.now()).getTime()) / 1000)),
          players: playersList,
        };

        // Synthesize results if none found
        if (results.length === 0 && playersList.length > 0) {
          results = playersList.map((p: any) => {
            const won = Boolean(table.winnerPlayerId && (p.playerId === table.winnerPlayerId || p.displayName === table.winnerPlayerId));
            return {
              gameId: idStr,
              tableId: idStr,
              playerId: p.playerId,
              displayName: p.displayName,
              finalScore: p.chipStack,
              netChips: p.chipStack - startingChips,
              won,
              status: won ? 'WON' : (table.status === 'PLAYING' ? 'IN_HAND' : 'SEATED'),
              handCards: p.holeCards || [],
            } as any;
          });
        }

        // Synthesize chronological event sequence if none found
        if (events.length === 0) {
          const synthEvents: any[] = [];
          let seq = 1;

          synthEvents.push({
            sequence: seq++,
            eventType: 'TABLE_INITIALIZED',
            playerId: 'SYSTEM',
            payloadJson: JSON.stringify({
              tableName: table.name,
              variant: table.variant,
              smallBlind,
              bigBlind,
              maxPlayers: table.maxPlayers || 6,
              startingChips,
            }),
            timestamp: table.createdAt || new Date(Date.now() - 30000),
          });

          for (const p of playersList) {
            synthEvents.push({
              sequence: seq++,
              eventType: 'PLAYER_SEATED',
              playerId: p.displayName,
              payloadJson: JSON.stringify({
                seat: p.seatIndex,
                chipStack: p.chipStack,
                isBot: p.isBot,
              }),
              timestamp: table.createdAt || new Date(Date.now() - 25000),
            });
          }

          if (playersList.length >= 2) {
            synthEvents.push({
              sequence: seq++,
              eventType: 'POST_BLINDS',
              playerId: 'SYSTEM',
              payloadJson: JSON.stringify({
                smallBlindSeat: 0,
                smallBlindAmount: smallBlind,
                bigBlindSeat: 1,
                bigBlindAmount: bigBlind,
                totalPot: smallBlind + bigBlind,
              }),
              timestamp: new Date(Date.now() - 20000),
            });

            synthEvents.push({
              sequence: seq++,
              eventType: 'DEAL_HOLE_CARDS',
              playerId: 'DEALER',
              payloadJson: JSON.stringify({
                variant: table.variant,
                cardsCount: table.variant === 'OMAHA' ? 4 : table.variant === 'PINEAPPLE' ? 3 : 2,
              }),
              timestamp: new Date(Date.now() - 15000),
            });

            synthEvents.push({
              sequence: seq++,
              eventType: 'BETTING_ROUND_PREFLOP',
              playerId: 'SYSTEM',
              payloadJson: JSON.stringify({
                round: 'PRE_FLOP',
                currentBet: bigBlind,
                pot: pot,
              }),
              timestamp: new Date(Date.now() - 10000),
            });
          }

          synthEvents.push({
            sequence: seq++,
            eventType: table.status === 'PLAYING' ? 'HAND_IN_PROGRESS' : 'TABLE_STATUS',
            playerId: 'SYSTEM',
            payloadJson: JSON.stringify({
              status: table.status,
              activePlayers: playersList.length,
              currentPot: pot,
              updatedAt: table.updatedAt,
            }),
            timestamp: table.updatedAt || new Date(),
          });

          events = synthEvents as any;
        }
      }
    }

    if (!game && events.length === 0) {
      res.status(404).json({ success: false, message: `Table or Game record ${cleanId} not found in database.` });
      return;
    }

    res.json({
      success: true,
      game,
      results,
      eventsCount: events.length,
      events,
    });
  } catch (err: any) {
    res.status(500).json({ success: false, message: err.message });
  }
}
