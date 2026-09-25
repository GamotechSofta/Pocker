package com.poker;

import com.poker.dto.AuthResponse;
import com.poker.dto.UserResponse;
import com.poker.repository.UserRepository;
import com.poker.support.AbstractMongoTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AuthIntegrationTest extends AbstractMongoTest {

    @LocalServerPort
    private int port;

    @Autowired
    private UserRepository userRepository;

    @Test
    void registerLoginAndMeSucceed() {
        RestClient client = client();
        String username = uniqueName();
        String email = username + "@example.com";

        ResponseEntity<AuthResponse> registered = client.post()
                .uri("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"username":"%s","email":"%s","password":"password1"}
                        """.formatted(username, email))
                .retrieve()
                .toEntity(AuthResponse.class);

        assertEquals(HttpStatus.CREATED, registered.getStatusCode());
        AuthResponse body = registered.getBody();
        assertNotNull(body);
        assertNotNull(body.token());
        assertEquals("Bearer", body.tokenType());
        assertEquals(username, body.user().username());
        assertEquals(email, body.user().email());

        String storedHash = userRepository.findByUsernameIgnoreCase(username).orElseThrow().getPasswordHash();
        assertTrue(storedHash.startsWith("$2"), "password must be stored as a BCrypt hash");
        assertFalse(storedHash.contains("password1"));

        ResponseEntity<AuthResponse> loggedIn = client.post()
                .uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"username":"%s","password":"password1"}
                        """.formatted(username))
                .retrieve()
                .toEntity(AuthResponse.class);

        assertEquals(HttpStatus.OK, loggedIn.getStatusCode());
        assertNotNull(loggedIn.getBody());

        ResponseEntity<UserResponse> me = client.get()
                .uri("/api/auth/me")
                .header("Authorization", "Bearer " + loggedIn.getBody().token())
                .retrieve()
                .toEntity(UserResponse.class);

        assertEquals(HttpStatus.OK, me.getStatusCode());
        assertNotNull(me.getBody());
        assertEquals(username, me.getBody().username());
        assertEquals(email, me.getBody().email());
    }

    @Test
    void loginAcceptsEmail() {
        RestClient client = client();
        String username = uniqueName();
        String email = username + "@example.com";
        register(client, username, email);

        AuthResponse loggedIn = client.post()
                .uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"username":"%s","password":"password1"}
                        """.formatted(email))
                .retrieve()
                .body(AuthResponse.class);

        assertNotNull(loggedIn);
        assertEquals(username, loggedIn.user().username());
    }

    @Test
    void duplicateUsernameIsRejected() {
        RestClient client = client();
        String username = uniqueName();
        register(client, username, username + "@example.com");

        RestClientResponseException ex = assertThrows(RestClientResponseException.class, () ->
                client.post()
                        .uri("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {"username":"%s","email":"%s","password":"password1"}
                                """.formatted(username, username + "2@example.com"))
                        .retrieve()
                        .toBodilessEntity()
        );

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        assertTrue(ex.getResponseBodyAsString().contains("USERNAME_TAKEN"));
    }

    @Test
    void invalidPasswordIsRejectedWithoutLeakingUser() {
        RestClient client = client();
        String username = uniqueName();
        register(client, username, username + "@example.com");

        RestClientResponseException ex = assertThrows(RestClientResponseException.class, () ->
                client.post()
                        .uri("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {"username":"%s","password":"wrong-password"}
                                """.formatted(username))
                        .retrieve()
                        .toBodilessEntity()
        );

        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
        assertTrue(ex.getResponseBodyAsString().contains("INVALID_CREDENTIALS"));
        assertTrue(ex.getResponseBodyAsString().contains("Invalid username or password."));
    }

    @Test
    void guestSessionIsIssuedWithoutCredentials() {
        RestClient client = client();
        ResponseEntity<AuthResponse> guest = client.post()
                .uri("/api/auth/guest")
                .retrieve()
                .toEntity(AuthResponse.class);

        assertEquals(HttpStatus.CREATED, guest.getStatusCode());
        AuthResponse body = guest.getBody();
        assertNotNull(body);
        assertNotNull(body.token());
        assertTrue(body.user().username().startsWith("g_"));

        ResponseEntity<UserResponse> me = client.get()
                .uri("/api/auth/me")
                .header("Authorization", "Bearer " + body.token())
                .retrieve()
                .toEntity(UserResponse.class);
        assertEquals(HttpStatus.OK, me.getStatusCode());
        assertEquals(body.user().username(), me.getBody().username());
    }

    @Test
    void meRequiresAuthentication() {
        RestClientResponseException ex = assertThrows(RestClientResponseException.class, () ->
                client().get().uri("/api/auth/me").retrieve().toBodilessEntity()
        );

        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
        assertTrue(ex.getResponseBodyAsString().contains("UNAUTHORIZED"));
    }

    @Test
    void registerValidatesPayload() {
        RestClientResponseException ex = assertThrows(RestClientResponseException.class, () ->
                client().post()
                        .uri("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {"username":"ab","email":"not-an-email","password":"short"}
                                """)
                        .retrieve()
                        .toBodilessEntity()
        );

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertTrue(ex.getResponseBodyAsString().contains("VALIDATION_ERROR"));
    }

    private RestClient client() {
        return RestClient.builder()
                .baseUrl("http://127.0.0.1:" + port)
                .build();
    }

    private void register(RestClient client, String username, String email) {
        client.post()
                .uri("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"username":"%s","email":"%s","password":"password1"}
                        """.formatted(username, email))
                .retrieve()
                .toBodilessEntity();
    }

    private static String uniqueName() {
        return "u" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }
}
