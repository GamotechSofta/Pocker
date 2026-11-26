package com.poker.config;

import com.poker.tournament.service.TournamentLifecycleService;
import com.poker.tournament.service.TournamentGameplayService;
import com.poker.tournament.service.TournamentFinalizationService;
import com.poker.tournament.service.TournamentOperations;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Clock;

@Configuration
@EnableScheduling
public class TournamentSchedulingConfig {
    @Bean
    Clock tournamentClock() { return Clock.systemUTC(); }

    @Bean
    @ConditionalOnProperty(name = "app.tournament.auto-start-enabled", havingValue = "true", matchIfMissing = true)
    TournamentScheduler tournamentScheduler(TournamentLifecycleService service, TournamentOperations operations) {
        return new TournamentScheduler(service, operations);
    }

    @Bean
    @ConditionalOnProperty(name = "app.tournament.gameplay-enabled", havingValue = "true", matchIfMissing = true)
    GameplayScheduler gameplayScheduler(TournamentGameplayService service, TournamentOperations operations) {
        return new GameplayScheduler(service, operations);
    }

    @Bean
    @ConditionalOnProperty(name = "app.tournament.gameplay-enabled", havingValue = "true", matchIfMissing = true)
    FinalizationScheduler finalizationScheduler(TournamentFinalizationService service, TournamentOperations operations) {
        return new FinalizationScheduler(service, operations);
    }

    record FinalizationScheduler(TournamentFinalizationService service, TournamentOperations operations) {
        @Scheduled(fixedDelayString = "${app.tournament.finalization-poll-ms:1200}")
        public void check() { operations.run("finalization", service::tick); }
    }

    record GameplayScheduler(TournamentGameplayService service, TournamentOperations operations) {
        @Scheduled(fixedDelayString = "${app.tournament.gameplay-poll-ms:500}")
        public void check() { operations.run("gameplay", service::tick); }
    }

    record TournamentScheduler(TournamentLifecycleService service, TournamentOperations operations) {
        @Scheduled(fixedDelayString = "${app.tournament.poll-ms:2500}")
        public void check() {
            operations.run("initialization", service::processDueTournaments);
        }
    }
}
