package com.poker.service;

import com.poker.dto.GameLaunchRequest;
import com.poker.entity.User;
import com.poker.exception.ApiException;
import com.poker.operations.*;
import com.poker.repository.UserRepository;
import com.poker.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AuthLaunchServiceTest {
    final UserRepository users = mock(UserRepository.class);
    final PasswordEncoder passwords = mock(PasswordEncoder.class);
    final JwtService jwt = mock(JwtService.class);
    final OperationsWalletClient operations = mock(OperationsWalletClient.class);
    final OperatorSessionRepository sessions = mock(OperatorSessionRepository.class);
    final Map<UUID, User> savedUsers = new HashMap<>();
    final Map<String, OperatorSession> savedSessions = new HashMap<>();
    final AuthService auth = new AuthService(users, passwords, jwt, operations, sessions);

    @BeforeEach void setUp() {
        when(users.findById(any(UUID.class))).thenAnswer(i -> Optional.ofNullable(savedUsers.get(i.getArgument(0))));
        when(users.insert(any(User.class))).thenAnswer(i -> { User u = i.getArgument(0); savedUsers.put(u.getId(), u); return u; });
        when(sessions.findByPlayerId(anyString())).thenAnswer(i -> Optional.ofNullable(savedSessions.get(i.getArgument(0))));
        when(sessions.insert(any(OperatorSession.class))).thenAnswer(i -> { OperatorSession s = i.getArgument(0); savedSessions.put(s.getPlayerId(), s); return s; });
        when(passwords.encode(anyString())).thenReturn("opaque-hash");
        when(jwt.createToken(any(User.class))).thenReturn("game-jwt");
        when(jwt.getExpiresInSeconds()).thenReturn(3600L);
        when(operations.balance(anyString(), anyString())).thenReturn(new BigDecimal("112.75"));
    }
    GameLaunchRequest request(String token, String gid) {
        return new GameLaunchRequest(token, " alpha-test ", gid, "poker-razz", "Alex", "player-7", "usd", "EN", "external", "https://operator.example/lobby");
    }
    @Test void returnsVerifiedBalanceAndProviderMetadata() {
        var result = auth.launch(request("provider-token", "1001"));
        assertEquals("Alex", result.user().username());
        assertEquals(new BigDecimal("112.75"), result.launch().balance());
        assertEquals("USD", result.launch().currency());
        assertEquals("en", result.launch().lang());
        assertEquals("poker-razz", result.launch().gameCode());
        assertEquals("external", result.launch().extsess());
        assertEquals("alpha-test", savedSessions.get(result.user().id().toString()).getPartnerCode());
        var order = inOrder(operations, jwt);
        order.verify(operations).balance("provider-token", "alpha-test");
        order.verify(jwt).createToken(any(User.class));
    }
    @Test void defaultsCurrencyAndLanguage() {
        var result = auth.launch(new GameLaunchRequest("token", "PARTNER", "1001", "holdem", "Alex", null, null, null, null, null));
        assertEquals("INR", result.launch().currency());
        assertEquals("en", result.launch().lang());
        assertNull(result.launch().closeUrl());
    }
    @Test void replayReusesIdentityButNewTokenAndGamePreservePreviousSession() {
        var first = auth.launch(request("one", "1001"));
        assertEquals(first.user().id(), auth.launch(request("one", "1001")).user().id());
        assertNotEquals(first.user().id(), auth.launch(request("two", "1001")).user().id());
        assertNotEquals(first.user().id(), auth.launch(request("one", "1002")).user().id());
        assertEquals(3, savedSessions.size());
        assertEquals("one", savedSessions.get(first.user().id().toString()).getToken());
        verify(sessions, never()).delete(any(OperatorSession.class));
        verify(sessions, never()).save(any(OperatorSession.class));
    }
    @Test void failedInitialBalanceCannotMintJwtOrCreateUser() {
        when(operations.balance(anyString(), anyString())).thenThrow(new ApiException(HttpStatus.UNAUTHORIZED, "OPERATOR_SESSION_EXPIRED", "Expired"));
        assertThrows(ApiException.class, () -> auth.launch(request("bad", "1001")));
        verifyNoInteractions(users, jwt, passwords);
        verify(sessions, never()).insert(any(OperatorSession.class));
    }
    @Test void invalidLaunchNeverContactsProvider() {
        assertThrows(ApiException.class, () -> auth.launch(new GameLaunchRequest("token", "P", "1001", "", "Alex", null, null, null, null, null)));
        for (String gid : List.of("", "   ", "bad\ngid")) assertThrows(ApiException.class, () -> auth.launch(request("t", gid)));
        for (String url : List.of("javascript:alert(1)", "//operator.example", "https://u:p@operator.example"))
            assertThrows(ApiException.class, () -> auth.launch(new GameLaunchRequest("t", "P", "1001", "razz", "Alex", null, null, null, null, url)));
        verifyNoInteractions(operations, users, jwt, sessions);
    }

    @Test void acceptsStringGameIdentifiersLikePokerRazzFromOperatorLobby() {
        var result = auth.launch(new GameLaunchRequest(
                "tok-razz", "123games", "POKERRAZZ", "Poker-Razz", "alex", "u-1", "INR", "en", null, null));
        assertEquals("POKERRAZZ", savedSessions.get(result.user().id().toString()).getGid());
        assertEquals("123games", savedSessions.get(result.user().id().toString()).getPartnerCode());
    }
    @Test void blockedSessionCannotBeReopenedByLaunchReplay() {
        var first = auth.launch(request("token", "1001"));
        savedSessions.get(first.user().id().toString()).blockWagering();
        clearInvocations(operations, jwt);
        assertEquals("OPERATOR_SESSION_EXPIRED", assertThrows(ApiException.class, () -> auth.launch(request("token", "1001"))).getCode());
        verifyNoInteractions(operations, jwt);
    }

    @Test void normalizesRealWorldLocaleCurrencyAndZeroPaddedGid() {
        var result = auth.launch(new GameLaunchRequest(
                "tok-norm", "partner", "001234567890123456", "Poker-FiveCard", "alex", null, " ₹ ", "en_IN", null, null));
        assertEquals("INR", result.launch().currency());
        assertEquals("en-in", result.launch().lang());
        assertEquals("1234567890123456", savedSessions.get(result.user().id().toString()).getGid());
    }
}
