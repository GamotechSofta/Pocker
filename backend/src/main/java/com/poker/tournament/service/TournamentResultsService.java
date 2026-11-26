package com.poker.tournament.service;

import com.poker.exception.ApiException;
import com.poker.tournament.dto.TournamentResults;
import com.poker.tournament.entity.*;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class TournamentResultsService {
    public enum HistoryFilter { ALL, WINS, TOP_THREE }

    private final TournamentTransactions transactions;

    public TournamentResultsService(TournamentTransactions transactions) {
        this.transactions = transactions;
    }

    public TournamentResults results(UUID id, UUID user, int page, int size) {
        validatePage(page, size);
        return transactions.run(tx -> {
            var event = tx.findById(id, Tournament.class);
            if (event == null || event.getStatus() == TournamentStatus.DRAFT || event.getStatus() == TournamentStatus.CANCELLED) throw unavailable();
            var summary = event.getCompletion();
            if (event.getVisibility() == TournamentVisibility.PRIVATE && !member(tx, event, user)) throw unavailable();
            if (event.getStatus() != TournamentStatus.COMPLETED || summary == null) {
                throw new ApiException(HttpStatus.CONFLICT, "TOURNAMENT_RESULTS_PENDING", "Official results are available after finalization.");
            }
            var query = official(summary).and("tournamentId").is(id);
            var podium = tx.find(Query.query(query).with(Sort.by("finishPosition")).limit(3), TournamentFinalResult.class);
            var rows = tx.find(Query.query(query).with(Sort.by("finishPosition")).skip((long) page * size).limit(size), TournamentFinalResult.class);
            var own = tx.findOne(Query.query(official(summary).and("tournamentId").is(id).and("userId").is(user)), TournamentFinalResult.class);
            return new TournamentResults(id, event.getStatus().name(), summary, podium.stream().map(row -> standing(row, user)).toList(),
                    rows.stream().map(row -> standing(row, user)).toList(), own == null ? null : standing(own, user),
                    page, size, summary.entrantCount(), Math.ceilDiv(summary.entrantCount(), size));
        });
    }

    private static boolean member(MongoTemplate tx, Tournament event, UUID user) {
        if (event.getCompletion() != null) {
            return tx.exists(Query.query(official(event.getCompletion()).and("tournamentId").is(event.getId()).and("userId").is(user)), TournamentFinalResult.class);
        }
        return tx.exists(Query.query(Criteria.where("tournamentId").is(event.getId()).and("userId").is(user)
                .and("status").ne(TournamentEntrantStatus.CANCELLED)), TournamentEntrant.class);
    }

    public TournamentResults.History history(UUID user, HistoryFilter filter, int page, int size) {
        validatePage(page, size);
        return transactions.run(tx -> {
            var base = personal(user);
            long completed = tx.count(Query.query(base), TournamentFinalResult.class);
            long wins = countRank(tx, user, Criteria.where("finishPosition").is(1));
            long runnerUp = countRank(tx, user, Criteria.where("finishPosition").is(2));
            long topThree = countRank(tx, user, Criteria.where("finishPosition").lte(3));
            var best = tx.findOne(Query.query(base).with(Sort.by("finishPosition")), TournamentFinalResult.class);
            var stats = new TournamentResults.Statistics(completed, wins, runnerUp, topThree, best == null ? null : best.getFinishPosition(),
                    completed == 0 ? null : 100.0 * wins / completed);
            var query = switch (filter) {
                case ALL -> base;
                case WINS -> personal(user).and("finishPosition").is(1);
                case TOP_THREE -> personal(user).and("finishPosition").lte(3);
            };
            long total = switch (filter) { case ALL -> completed; case WINS -> wins; case TOP_THREE -> topThree; };
            var rows = tx.find(Query.query(query).with(Sort.by(Sort.Order.desc("completedAt"), Sort.Order.asc("id")))
                    .skip((long) page * size).limit(size), TournamentFinalResult.class);
            var ids = rows.stream().map(TournamentFinalResult::getTournamentId).toList();
            var events = tx.find(Query.query(Criteria.where("id").in(ids).and("status").is(TournamentStatus.COMPLETED)), Tournament.class)
                    .stream().filter(event -> event.getCompletion() != null).collect(Collectors.toMap(Tournament::getId, Function.identity()));
            var history = rows.stream().filter(row -> events.containsKey(row.getTournamentId())).map(row -> {
                var summary = events.get(row.getTournamentId()).getCompletion();
                return new TournamentResults.HistoryItem(row.getTournamentId(), summary.tournamentName(), summary.banner(),
                        summary.startMode(), summary.tableMode(), summary.entrantCount(), row.getFinishPosition(),
                        row.getCompletedAt(), row.getParticipationStatus());
            }).toList();
            return new TournamentResults.History(history, stats, page, size, total, Math.ceilDiv(total, size));
        });
    }

    private static long countRank(MongoTemplate tx, UUID user, Criteria rank) {
        return tx.count(Query.query(personal(user)).addCriteria(rank), TournamentFinalResult.class);
    }

    private static Criteria personal(UUID user) { return Criteria.where("userId").is(user).and("resultVersion").gte(1); }
    private static Criteria official(TournamentCompletion summary) {
        return Criteria.where("operationId").is(summary.operationId()).and("resultVersion").is(summary.resultVersion());
    }
    private static TournamentResults.Standing standing(TournamentFinalResult result, UUID viewer) {
        return new TournamentResults.Standing(result.getId(), result.getEntrantId(), result.getDisplayName(), result.getFinishPosition(),
                result.getFinalVirtualChips(), result.getParticipationStatus(), result.getTableNumber(), result.getHandsPlayed(),
                result.getEliminatedAt(), result.getCompletedAt(), result.getUserId().equals(viewer));
    }
    private static void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > 50) throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PAGE", "Use a nonnegative page and a size between 1 and 50.");
    }
    private static ApiException unavailable() {
        return new ApiException(HttpStatus.NOT_FOUND, "TOURNAMENT_NOT_FOUND", "Tournament results are unavailable.");
    }
}
