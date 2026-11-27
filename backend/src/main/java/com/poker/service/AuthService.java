package com.poker.service;

import com.poker.dto.AuthResponse;
import com.poker.dto.LoginRequest;
import com.poker.dto.RegisterRequest;
import com.poker.dto.UserResponse;
import com.poker.dto.GameLaunchRequest;
import com.poker.dto.LaunchMetadata;
import com.poker.entity.User;
import com.poker.exception.EmailTakenException;
import com.poker.exception.InvalidCredentialsException;
import com.poker.exception.PlayerNotFoundException;
import com.poker.exception.UsernameTakenException;
import com.poker.exception.ApiException;
import com.poker.exception.ErrorCodes;
import com.poker.mapper.UserMapper;
import com.poker.repository.UserRepository;
import com.poker.security.JwtService;
import com.poker.operations.OperationsWalletClient;
import com.poker.operations.OperatorSession;
import com.poker.operations.OperatorSessionRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.math.BigDecimal;
import java.net.URI;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final OperationsWalletClient operationsWallet;
    private final OperatorSessionRepository operatorSessions;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            OperationsWalletClient operationsWallet, OperatorSessionRepository operatorSessions
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.operationsWallet = operationsWallet;
        this.operatorSessions = operatorSessions;
    }

    public AuthResponse register(RegisterRequest request) {
        String username = normalizeUsername(request.username());
        String email = normalizeEmail(request.email());

        if (userRepository.existsByUsernameIgnoreCase(username)) {
            throw new UsernameTakenException();
        }
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new EmailTakenException();
        }

        User user = userRepository.save(new User(
                username,
                email,
                passwordEncoder.encode(request.password())
        ));
        return toAuthResponse(user);
    }

    public AuthResponse createGuest() {
        for (int attempt = 0; attempt < 8; attempt++) {
            String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
            String username = "g_" + suffix;
            String email = username + "@guest.local";
            if (userRepository.existsByUsernameIgnoreCase(username) || userRepository.existsByEmailIgnoreCase(email)) {
                continue;
            }
            String password = UUID.randomUUID().toString() + UUID.randomUUID();
            User user = userRepository.save(new User(
                    username,
                    email,
                    passwordEncoder.encode(password)
            ));
            return toAuthResponse(user);
        }
        throw new IllegalStateException("Could not allocate a guest username.");
    }

    public AuthResponse login(LoginRequest request) {
        String identifier = request.username().trim();
        User user = findByUsernameOrEmail(identifier)
                .orElseThrow(InvalidCredentialsException::new);

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }
        return toAuthResponse(user);
    }

    /** Verifies the launch token through Operations before minting our short-lived game JWT. */
    public AuthResponse launch(GameLaunchRequest request) {
        GameLaunchRequest launch = normalizedLaunch(request);
        String identity = "op_" + digest(launch.partnerCode() + "\n" + launch.token() + "\n" + launch.gid());
        UUID playerId = UUID.nameUUIDFromBytes(identity.getBytes(StandardCharsets.UTF_8));
        OperatorSession existing = operatorSessions.findByPlayerId(playerId.toString()).orElse(null);
        if (existing != null && existing.isWageringBlocked()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "OPERATOR_SESSION_EXPIRED",
                    "This game session has ended. Return to your operator lobby.");
        }
        BigDecimal balance = operationsWallet.balance(launch.token(), launch.partnerCode());
        User user = findOrCreateOperatorUser(playerId, identity, launch.username());
        OperatorSession session = existing;
        if (session == null) {
            OperatorSession candidate = new OperatorSession(playerId.toString(), launch.token(), launch.partnerCode(),
                    launch.gid(), launch.gameCode(), launch.username(), launch.userId(), launch.currency(),
                    launch.lang(), launch.extsess(), launch.closeUrl());
            try {
                session = operatorSessions.insert(candidate);
            } catch (DuplicateKeyException concurrentLaunch) {
                session = operatorSessions.findByPlayerId(playerId.toString()).orElseThrow(() -> concurrentLaunch);
            }
        }
        if (session.isWageringBlocked()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "OPERATOR_SESSION_EXPIRED",
                    "This game session has ended. Return to your operator lobby.");
        }
        return new AuthResponse(jwtService.createToken(user), "Bearer", jwtService.getExpiresInSeconds(),
                UserMapper.toResponse(user), LaunchMetadata.from(session, balance));
    }

    private User findOrCreateOperatorUser(UUID id, String identity, String displayName) {
        Optional<User> existing = userRepository.findById(id);
        if (existing.isPresent()) return existing.get();
        User candidate = new User(id, identity, identity + "@operator.local",
                passwordEncoder.encode(UUID.randomUUID().toString()));
        candidate.setDisplayName(displayName);
        try {
            return userRepository.insert(candidate);
        } catch (DuplicateKeyException concurrentLaunch) {
            return userRepository.findById(id).orElseThrow(() -> concurrentLaunch);
        }
    }

    private static GameLaunchRequest normalizedLaunch(GameLaunchRequest request) {
        if (request == null) throw invalidLaunch("Launch request is required.");
        String token = launchValue(request.token(), "token", 2048, true);
        String partner = launchValue(request.partnerCode(), "partner_code", 128, true).toUpperCase(Locale.ROOT);
        String gid = launchValue(request.gid(), "gid", 32, true).replaceFirst("^0+(?=[1-9])", "");
        if (!gid.matches("[1-9][0-9]{0,17}")) throw invalidLaunch("gid must be a positive integer.");
        String gameCode = launchValue(request.gameCode(), "game_code", 128, true);
        String username = launchValue(request.username(), "username", 128, true);
        String userId = launchValue(request.userId(), "user_id", 256, false);
        String currency = launchValue(request.currency(), "currency", 16, false);
        currency = currency == null ? "INR" : switch (currency.toUpperCase(Locale.ROOT)) {
            case "₹", "RS", "RUPEE", "RUPEES" -> "INR";
            default -> currency.toUpperCase(Locale.ROOT);
        };
        if (!currency.matches("[A-Z0-9]{2,12}")) throw invalidLaunch("currency must be an ISO code.");
        String lang = launchValue(request.lang(), "lang", 35, false);
        if (lang == null) {
            lang = "en";
        } else {
            String normalizedLang = lang.replace('_', '-').toLowerCase(Locale.ROOT);
            lang = switch (normalizedLang) {
                case "english" -> "en";
                case "hindi" -> "hi";
                default -> normalizedLang.matches("[a-z]{2,8}(-[a-z0-9]{1,8})*") ? normalizedLang : "en";
            };
        }
        String extsess = launchValue(request.extsess(), "extsess", 512, false);
        String closeUrl = launchValue(request.closeUrl(), "close_url", 2048, false);
        if (closeUrl != null) {
            try {
                URI uri = URI.create(closeUrl);
                if (!("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                        || uri.getHost() == null || uri.getRawUserInfo() != null) {
                    throw invalidLaunch("close_url must be an absolute HTTP(S) lobby URL without credentials.");
                }
            } catch (IllegalArgumentException invalidUri) {
                throw invalidLaunch("close_url must be a valid HTTP(S) lobby URL.");
            }
        }
        return new GameLaunchRequest(token, partner, gid, gameCode, username, userId, currency, lang, extsess, closeUrl);
    }

    private static String launchValue(String raw, String field, int maxLength, boolean required) {
        String value = raw == null ? null : raw.trim();
        if (value == null || value.isEmpty()) {
            if (required) throw invalidLaunch(field + " is required.");
            return null;
        }
        if (value.length() > maxLength || value.chars().anyMatch(Character::isISOControl)) {
            throw invalidLaunch(field + " is invalid.");
        }
        return value;
    }

    private static ApiException invalidLaunch(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, ErrorCodes.VALIDATION_ERROR, message);
    }

    public UserResponse me(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new PlayerNotFoundException(userId));
        return UserMapper.toResponse(user);
    }

    private Optional<User> findByUsernameOrEmail(String identifier) {
        if (identifier.contains("@")) {
            return userRepository.findByEmailIgnoreCase(normalizeEmail(identifier));
        }
        return userRepository.findByUsernameIgnoreCase(normalizeUsername(identifier));
    }

    private AuthResponse toAuthResponse(User user) {
        return new AuthResponse(
                jwtService.createToken(user),
                "Bearer",
                jwtService.getExpiresInSeconds(),
                UserMapper.toResponse(user)
        );
    }

    private static String normalizeUsername(String username) {
        return username.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static String digest(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            for (byte valueByte : bytes) result.append(String.format("%02x", valueByte));
            return result.toString();
        } catch (Exception error) {
            throw new IllegalStateException("Could not derive operator identity", error);
        }
    }
}
