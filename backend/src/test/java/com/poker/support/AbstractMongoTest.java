package com.poker.support;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.UUID;

public abstract class AbstractMongoTest {

    /**
     * Set POKER_TEST_MONGO_URI (e.g. mongodb://localhost:27017) to run against an existing server
     * when Docker is unavailable; each run then gets its own throwaway database.
     */
    private static final String EXTERNAL_MONGO = System.getenv("POKER_TEST_MONGO_URI");

    static final MongoDBContainer MONGO = EXTERNAL_MONGO == null || EXTERNAL_MONGO.isBlank()
            ? new MongoDBContainer(DockerImageName.parse("mongo:7"))
            : null;

    private static final String CONNECTION_STRING;

    static {
        String database = "poker_it_" + UUID.randomUUID().toString().substring(0, 8);
        if (MONGO != null) {
            MONGO.start();
            CONNECTION_STRING = MONGO.getConnectionString() + "/" + database;
        } else {
            String base = EXTERNAL_MONGO.endsWith("/") ? EXTERNAL_MONGO : EXTERNAL_MONGO + "/";
            CONNECTION_STRING = base + database;
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try (MongoClient client = MongoClients.create(EXTERNAL_MONGO)) {
                    client.getDatabase(database).drop();
                } catch (RuntimeException ignored) {
                    // best effort: a leftover test database is harmless
                }
            }));
        }
        // Spring's test context must see the same URI even when a test class
        // inherits this fixture through an abstract superclass. The dynamic
        // property remains registered below for normal context precedence.
        System.setProperty("spring.mongodb.uri", CONNECTION_STRING);
        System.setProperty("spring.mongodb.representation.uuid", "standard");
    }

    @DynamicPropertySource
    static void mongoProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.mongodb.uri", () -> CONNECTION_STRING);
        registry.add("spring.mongodb.representation.uuid", () -> "standard");
        registry.add("MONGODB_URI", () -> CONNECTION_STRING);
        registry.add("JWT_SECRET", () -> "test-jwt-secret-key-that-is-32-bytes!");
    }
}
