package com.poker;

import com.poker.support.AbstractMongoTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestClient;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class FoundationIntegrationTest extends AbstractMongoTest {

    @LocalServerPort
    private int port;

    @Autowired
    private MongoTemplate mongoTemplate;

    @Test
    void mongoDatabaseIsReachable() {
        assertNotNull(mongoTemplate.getDb());
        assertTrue(mongoTemplate.getDb().getName().length() > 0);
    }

    @Test
    void actuatorHealthIsUp() {
        RestClient client = RestClient.builder()
                .baseUrl("http://127.0.0.1:" + port)
                .build();

        ResponseEntity<Map> response = client.get()
                .uri("/actuator/health")
                .retrieve()
                .toEntity(Map.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("UP", response.getBody().get("status"));
        assertTrue(response.getBody().containsKey("components"));
    }
}
