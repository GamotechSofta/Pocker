package com.poker.tournament.service;

import com.poker.entity.PokerTable;
import com.poker.tournament.entity.Tournament;
import com.poker.tournament.entity.TournamentStatus;
import com.poker.websocket.GameSocketConnections;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.lang.management.ManagementFactory;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class TournamentOperations {
    private static final Logger log = LoggerFactory.getLogger(TournamentOperations.class);
    private final Map<String, Instant> lastSuccessful = new ConcurrentHashMap<>();
    private final MongoTemplate mongo;
    private final GameSocketConnections sockets;
    private final MeterRegistry metrics;
    private final String nodeId;

    public TournamentOperations(MongoTemplate mongo, GameSocketConnections sockets, MeterRegistry metrics,
                                @Value("${POKER_RUNTIME_NODE:local}") String nodeId) {
        this.mongo = mongo;
        this.sockets = sockets;
        this.metrics = metrics;
        this.nodeId = nodeId;
    }

    public String nodeId() { return nodeId; }

    public void run(String worker, Runnable action) {
        var sample = Timer.start(metrics);
        try {
            action.run();
            lastSuccessful.put(worker, Instant.now());
        } catch (RuntimeException failed) {
            metrics.counter("poker.tournament.scheduler.failures", "worker", worker).increment();
            log.atWarn().addKeyValue("worker", worker).addKeyValue("owner", nodeId)
                    .addKeyValue("exception", failed.getClass().getSimpleName()).log("Tournament scheduler failed");
        } finally {
            sample.stop(metrics.timer("poker.tournament.scheduler.run", "worker", worker));
        }
    }

    public Map<String, Object> snapshot() {
        var result = new LinkedHashMap<String, Object>();
        result.put("node", nodeId);
        result.put("webSockets", sockets.count());
        var heap = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
        result.put("heapUsedBytes", heap.getUsed());
        result.put("heapMaxBytes", heap.getMax());
        result.put("liveThreads", ManagementFactory.getThreadMXBean().getThreadCount());
        if (ManagementFactory.getOperatingSystemMXBean() instanceof com.sun.management.OperatingSystemMXBean process) {
            result.put("processCpuLoad", process.getProcessCpuLoad());
        }
        result.put("activeTournaments", count(Criteria.where("status").is(TournamentStatus.RUNNING)));
        result.put("activeTables", mongo.count(Query.query(Criteria.where("gameMode").is("TOURNAMENT")
                .and("gameplay.phase").is("ACTIVE").and("tournamentClosedAt").isNull()), PokerTable.class));
        result.put("pendingInitialization", count(Criteria.where("status").is(TournamentStatus.STARTING)));
        result.put("pendingTransfers", count(Criteria.where("topology.pending.stage").in("WAITING_FOR_HANDS", "DESTINATION_RESERVED")));
        result.put("pendingFinalization", count(Criteria.where("status").is(TournamentStatus.PAUSED)
                .and("standings.candidate.entrantId").exists(true).and("finalization.stage").ne("RECOVERY_REQUIRED")));
        result.put("pausedTournaments", count(Criteria.where("status").is(TournamentStatus.PAUSED)));
        result.put("recoveryRequired", count(new Criteria().orOperator(Criteria.where("finalization.stage").is("RECOVERY_REQUIRED"),
                Criteria.where("topology.pending.stage").is("RECOVERY_REQUIRED"), Criteria.where("standings.reviewRequired").is(true),
                Criteria.where("blindClock.pauseReason").is("HAND_RECOVERY_REQUIRED"))));
        result.put("schedulerLastSuccessAt", Map.copyOf(lastSuccessful));
        result.put("schedulerFailures", metrics.find("poker.tournament.scheduler.failures").counters().stream().mapToDouble(counter -> counter.count()).sum());
        result.put("settlementIntegrityFailures", metrics.counter("poker.tournament.settlement.integrity.failures").count());
        var http = metrics.find("http.server.requests").timers();
        result.put("httpRequests", http.stream().mapToLong(Timer::count).sum());
        result.put("httpServerErrors", http.stream().filter(timer -> timer.getId().getTag("status") != null
                && timer.getId().getTag("status").startsWith("5")).mapToLong(Timer::count).sum());
        return result;
    }

    private long count(Criteria criteria) { return mongo.count(Query.query(criteria), Tournament.class); }
}
