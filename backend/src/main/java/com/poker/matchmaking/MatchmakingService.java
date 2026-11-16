package com.poker.matchmaking;

import com.poker.dto.CreateTableRequest;
import com.poker.dto.JoinTableRequest;
import com.poker.dto.MatchmakingRequest;
import com.poker.dto.MatchmakingTicketResponse;
import com.poker.dto.TableDetailResponse;
import com.poker.service.TableService;
import com.poker.wallet.StakeEscrowService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Service
public class MatchmakingService {

    private static final Logger log = LoggerFactory.getLogger(MatchmakingService.class);

    private final TableService tableService;
    private final StakeEscrowService stakeEscrowService;
    private final long botFallbackMs;
    private final long expiryMs;
    private final Map<UUID, Ticket> tickets = new ConcurrentHashMap<>();
    private final Map<String, ConcurrentLinkedQueue<UUID>> queues = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    public MatchmakingService(
            TableService tableService,
            StakeEscrowService stakeEscrowService,
            @Value("${poker.matchmaking.bot-fallback-ms:1500}") long botFallbackMs,
            @Value("${poker.matchmaking.expiry-ms:30000}") long expiryMs
    ) {
        this.tableService = tableService;
        this.stakeEscrowService = stakeEscrowService;
        this.botFallbackMs = botFallbackMs;
        this.expiryMs = expiryMs;
    }

    @PostConstruct
    void start() {
        scheduler.scheduleWithFixedDelay(this::processSafely, 100, 200, TimeUnit.MILLISECONDS);
    }

    public MatchmakingTicketResponse enqueue(UUID userId, MatchmakingRequest request) {
        Config conf = config(request);
        var seated = tableService.seatedTableId(userId);
        if (seated.isPresent()) {
            boolean shouldResume = true;
            try {
                TableDetailResponse detail = tableService.getTable(seated.get());
                if (detail != null && detail.variant() != null && !detail.variant().equalsIgnoreCase(conf.variant())) {
                    shouldResume = false;
                    tableService.leaveTable(seated.get(), userId);
                }
            } catch (RuntimeException ex) {
                // Keep resume if getTable fails
            }
            if (shouldResume) {
                Ticket resumed = Ticket.matched(userId, conf, seated.get());
                tickets.put(resumed.id, resumed);
                return resumed.response();
            }
        }

        tickets.values().stream()
                .filter(ticket -> ticket.userId.equals(userId) && ticket.status == Status.QUEUED)
                .forEach(this::cancelAndRefund);

        String escrowId = stakeEscrowService.reserveEntry(
                userId.toString(),
                conf.startingChips,
                "Matchmaking buy-in"
        );

        Ticket ticket = new Ticket(UUID.randomUUID(), userId, conf, escrowId);
        tickets.put(ticket.id, ticket);

        try {
            matchOrCreateSingle(ticket);
        } catch (RuntimeException ex) {
            refundTicket(ticket, "Match failed, entry returned");
            ticket.status = Status.EXPIRED;
            throw ex;
        }

        return ticket.response();
    }

    public MatchmakingTicketResponse get(UUID ticketId, UUID userId) {
        Ticket ticket = requireTicket(ticketId);
        if (!ticket.userId.equals(userId)) {
            throw new IllegalArgumentException("Matchmaking ticket does not belong to this player.");
        }
        return ticket.response();
    }

    public MatchmakingTicketResponse cancel(UUID ticketId, UUID userId) {
        Ticket ticket = requireTicket(ticketId);
        if (!ticket.userId.equals(userId)) {
            throw new IllegalArgumentException("Matchmaking ticket does not belong to this player.");
        }
        if (ticket.status == Status.QUEUED) {
            cancelAndRefund(ticket);
        }
        return ticket.response();
    }

    private void cancelAndRefund(Ticket ticket) {
        ticket.status = Status.CANCELLED;
        refundTicket(ticket, "Search cancelled, entry returned");
    }

    private void refundTicket(Ticket ticket, String reason) {
        if (ticket.escrowId == null || ticket.escrowRefunded) {
            return;
        }
        try {
            if (stakeEscrowService.refundStake(ticket.escrowId, reason)) {
                ticket.escrowRefunded = true;
                ticket.escrowId = null;
            }
        } catch (RuntimeException ex) {
            log.error("Failed to refund matchmaking stake {}: {}", ticket.escrowId, ex.getMessage());
        }
    }

    private void processSafely() {
        try {
            processQueues();
        } catch (RuntimeException ex) {
            log.error("Error processing matchmaking queues: {}", ex.getMessage(), ex);
        }
    }

    synchronized void processQueues() {
        Instant now = Instant.now();
        for (var entry : queues.entrySet()) {
            ConcurrentLinkedQueue<UUID> queue = entry.getValue();
            List<Ticket> waiting = new ArrayList<>();
            UUID ticketId;
            while ((ticketId = queue.poll()) != null) {
                Ticket ticket = tickets.get(ticketId);
                if (ticket != null && ticket.status == Status.QUEUED) {
                    waiting.add(ticket);
                }
            }
            if (waiting.isEmpty()) {
                continue;
            }

            int target = waiting.getFirst().config.maxPlayers;
            while (waiting.size() >= target) {
                List<Ticket> group = new ArrayList<>(waiting.subList(0, target));
                waiting.subList(0, target).clear();
                createMatch(group, true);
            }

            for (Ticket ticket : waiting) {
                long elapsed = Duration.between(ticket.createdAt, now).toMillis();
                if (ticket.config.allowBotFallback && elapsed >= botFallbackMs) {
                    matchOrCreateSingle(ticket);
                } else if (elapsed >= expiryMs) {
                    ticket.status = Status.EXPIRED;
                    refundTicket(ticket, "Search expired, entry returned");
                } else {
                    queue.add(ticket.id);
                }
            }
        }
    }

    private void matchOrCreateSingle(Ticket ticket) {
        var openTable = tableService.listTables().stream()
                .filter(t -> t.variant().equalsIgnoreCase(ticket.config.variant))
                .filter(t -> t.smallBlind() == ticket.config.smallBlind
                        && t.bigBlind() == ticket.config.bigBlind
                        && t.maxPlayers() == ticket.config.maxPlayers)
                .filter(t -> t.status().equalsIgnoreCase("WAITING") && t.playerCount() < t.maxPlayers())
                .findFirst();

        if (openTable.isPresent()) {
            try {
                seatWithEscrow(openTable.get().id(), ticket);
                ticket.matchedTableId = openTable.get().id();
                ticket.status = Status.MATCHED;
                return;
            } catch (RuntimeException ex) {
                log.debug("Could not join open table {}: {}", openTable.get().id(), ex.getMessage());
            }
        }

        createMatch(List.of(ticket), false);
    }

    private void createMatch(List<Ticket> group, boolean fullHumanTable) {
        Ticket first = group.getFirst();
        try {
            String label = first.config.variant.replace('_', ' ');
            TableDetailResponse table = tableService.createTable(first.userId, new CreateTableRequest(
                    label + " Quick Match",
                    first.config.variant,
                    first.config.smallBlind,
                    first.config.bigBlind,
                    first.config.maxPlayers,
                    first.config.startingChips
            ));
            for (Ticket ticket : group) {
                seatWithEscrow(table.id(), ticket);
            }
            if (fullHumanTable) {
                tableService.startTable(table.id(), first.userId);
            }
            for (Ticket ticket : group) {
                ticket.matchedTableId = table.id();
                ticket.status = Status.MATCHED;
            }
        } catch (RuntimeException ex) {
            log.error("Failed to create match for group: {}", ex.getMessage(), ex);
            group.forEach(ticket -> {
                ticket.status = Status.EXPIRED;
                refundTicket(ticket, "Match failed, entry returned");
            });
        }
    }

    private void seatWithEscrow(UUID tableId, Ticket ticket) {
        tableService.joinTable(tableId, ticket.userId, new JoinTableRequest(null, ticket.escrowId));
        // Escrow ownership transferred to the table seat; do not refund on later cancel.
        ticket.escrowId = null;
    }

    private Ticket requireTicket(UUID ticketId) {
        Ticket ticket = tickets.get(ticketId);
        if (ticket == null) {
            throw new IllegalArgumentException("Matchmaking ticket was not found.");
        }
        return ticket;
    }

    private static Config config(MatchmakingRequest request) {
        String variant = request.variant() == null || request.variant().isBlank()
                ? "TEXAS_HOLDEM"
                : request.variant().trim().toUpperCase(Locale.ROOT);
        int smallBlind = valueOr(request.smallBlind(), 10);
        int bigBlind = valueOr(request.bigBlind(), 20);
        int maxPlayers = Math.max(2, Math.min(10, valueOr(request.maxPlayers(), 6)));
        int startingChips = valueOr(request.startingChips(), 1000);
        boolean allowBots = request.allowBotFallback() == null || request.allowBotFallback();
        return new Config(variant, smallBlind, bigBlind, maxPlayers, startingChips, allowBots);
    }

    private static int valueOr(Integer value, int fallback) {
        return value == null ? fallback : value;
    }

    @PreDestroy
    void stop() {
        scheduler.shutdownNow();
    }

    private enum Status {
        QUEUED,
        MATCHED,
        CANCELLED,
        EXPIRED
    }

    private record Config(
            String variant,
            int smallBlind,
            int bigBlind,
            int maxPlayers,
            int startingChips,
            boolean allowBotFallback
    ) {
        String queueKey() {
            return variant + ":" + smallBlind + ":" + bigBlind + ":" + maxPlayers + ":" + startingChips;
        }
    }

    private static final class Ticket {
        private final UUID id;
        private final UUID userId;
        private final Config config;
        private final Instant createdAt;
        private volatile Status status;
        private volatile UUID matchedTableId;
        private volatile String escrowId;
        private volatile boolean escrowRefunded;

        private Ticket(UUID id, UUID userId, Config config, String escrowId) {
            this.id = id;
            this.userId = userId;
            this.config = config;
            this.escrowId = escrowId;
            this.createdAt = Instant.now();
            this.status = Status.QUEUED;
        }

        static Ticket matched(UUID userId, Config config, UUID tableId) {
            Ticket ticket = new Ticket(UUID.randomUUID(), userId, config, null);
            ticket.matchedTableId = tableId;
            ticket.status = Status.MATCHED;
            return ticket;
        }

        MatchmakingTicketResponse response() {
            return new MatchmakingTicketResponse(id, status.name(), matchedTableId, createdAt);
        }
    }
}
