package com.poker.config;

import org.bson.Document;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;
import java.util.concurrent.TimeUnit;

@Component("tournamentMongo")
public class TournamentMongoHealth implements HealthIndicator {
    private final MongoTemplate mongo;

    public TournamentMongoHealth(MongoTemplate mongo) { this.mongo = mongo; }

    @Override
    public Health health() {
        try {
            var hello = mongo.execute(db -> db.withTimeout(3, TimeUnit.SECONDS).runCommand(new Document("hello", 1)));
            if (hello.getString("setName") != null && Boolean.TRUE.equals(hello.getBoolean("isWritablePrimary"))
                    && hello.get("logicalSessionTimeoutMinutes") instanceof Number) return Health.up().build();
            return Health.down().withDetail("reason", "Writable replica-set primary with sessions required").build();
        } catch (RuntimeException unavailable) {
            return Health.down().withDetail("reason", "MongoDB readiness check failed").build();
        }
    }
}
