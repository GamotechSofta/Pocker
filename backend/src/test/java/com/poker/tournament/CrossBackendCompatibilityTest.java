package com.poker.tournament;

import com.poker.game.model.GameMode;
import com.poker.tournament.dto.TournamentResponse;
import com.poker.tournament.entity.BlindLevel;
import com.poker.tournament.entity.TournamentStartMode;
import com.poker.tournament.entity.TournamentStatus;
import com.poker.tournament.entity.TournamentTableMode;
import com.poker.tournament.entity.TournamentVisibility;
import com.poker.tournament.repository.TournamentBlindScheduleRepository;
import com.poker.tournament.repository.TournamentRepository;
import com.poker.tournament.service.TournamentService;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class CrossBackendCompatibilityTest {

    @DynamicPropertySource
    static void sharedMongoProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.mongodb.uri", () -> requiredEnvironment("TOURNAMENT_VERIFY_MONGO_URI"));
        registry.add("app.jwt.secret", () -> "isolated-cross-backend-test-secret-2026");
    }

    @Autowired
    private TournamentRepository tournamentRepository;

    @Autowired
    private TournamentBlindScheduleRepository blindScheduleRepository;

    @Autowired
    private TournamentService tournamentService;

    @Test
    void readsTournamentWrittenByAdminBackend() throws IOException {
        var fixture = Document.parse(Files.readString(Path.of(requiredEnvironment("TOURNAMENT_VERIFY_FIXTURE"))));
        var row = fixture.get("tournament", Document.class);
        var scheduleRow = fixture.get("schedule", Document.class);
        var id = UUID.fromString(row.getString("id"));
        var tournament = tournamentRepository.findById(id).orElseThrow();
        assertEquals(id, tournament.getId());
        assertEquals(GameMode.TOURNAMENT, tournament.getGameMode());
        assertEquals(TournamentStatus.CANCELLED, tournament.getStatus());
        assertEquals(TournamentVisibility.PRIVATE, tournament.getVisibility());
        assertTrue(tournament.getPrivateInviteCode().matches("[0-9A-F]{8}"));

        assertEquals(new TournamentResponse(
                id, row.getString("name"), row.getString("description"), row.getString("banner"),
                TournamentStartMode.valueOf(row.getString("startMode")),
                TournamentTableMode.valueOf(row.getString("tableMode")), TournamentVisibility.PRIVATE,
                GameMode.TOURNAMENT, row.getInteger("maximumEntrants"), row.getInteger("minimumEntrants"),
                row.getInteger("seatsPerTable"), row.get("startingVirtualChips", Number.class).longValue(),
                instant(row, "registrationOpeningAt"), instant(row, "registrationClosingAt"),
                instant(row, "scheduledStartAt"), row.getString("privateInviteCode"),
                UUID.fromString(row.getString("creatorId")), TournamentStatus.CANCELLED,
                instant(row, "createdAt"), instant(row, "updatedAt"), instant(row, "startedAt"),
                instant(row, "completedAt")), tournamentService.get(id));

        var schedule = blindScheduleRepository.findByTournamentId(id).orElseThrow();
        assertEquals(id, schedule.getTournamentId());
        assertEquals(UUID.fromString(scheduleRow.getString("_id")), schedule.getId());
        assertEquals(instant(scheduleRow, "createdAt"), schedule.getCreatedAt());
        List<BlindLevel> levels = scheduleRow.getList("levels", Document.class).stream()
                .map(level -> new BlindLevel(level.getInteger("levelNumber"), level.getInteger("smallBlind"),
                        level.getInteger("bigBlind"), level.getInteger("ante"), level.getInteger("durationSeconds"),
                        level.getBoolean("breakAfter")))
                .toList();
        assertEquals(levels, schedule.getLevels());
    }

    private static Instant instant(Document row, String field) {
        String value = row.getString(field);
        return value == null ? null : Instant.parse(value);
    }

    private static String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is required for the Express-to-Java compatibility test");
        }
        return value;
    }
}
