package com.poker.service;

import com.poker.dto.AuthResponse;
import com.poker.dto.LoginRequest;
import com.poker.dto.RegisterRequest;
import com.poker.dto.UserResponse;
import com.poker.dto.GameLaunchRequest;
import com.poker.entity.User;
import com.poker.exception.EmailTakenException;
import com.poker.exception.InvalidCredentialsException;
import com.poker.exception.PlayerNotFoundException;
import com.poker.exception.UsernameTakenException;
import com.poker.mapper.UserMapper;
import com.poker.repository.UserRepository;
import com.poker.security.JwtService;
import com.poker.operations.OperationsWalletClient;
import com.poker.operations.OperatorSession;
import com.poker.operations.OperatorSessionRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

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
        operationsWallet.balance(request.token().trim(), request.partnerCode().trim());
        String identity = "op_" + digest(request.partnerCode() + ":" + (request.userId() == null ? request.token() : request.userId()));
        User user = userRepository.findByUsernameIgnoreCase(identity).orElseGet(() ->
                userRepository.save(new User(identity, identity + "@operator.local", passwordEncoder.encode(UUID.randomUUID().toString()))));
        operatorSessions.findByPlayerId(user.getId().toString()).ifPresent(operatorSessions::delete);
        operatorSessions.save(new OperatorSession(user.getId().toString(), request.token().trim(), request.partnerCode().trim(), request.gid().trim(), request.closeUrl()));
        return toAuthResponse(user);
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
            for (int i = 0; i < 12; i++) result.append(String.format("%02x", bytes[i]));
            return result.toString();
        } catch (Exception error) {
            throw new IllegalStateException("Could not derive operator identity", error);
        }
    }
}
