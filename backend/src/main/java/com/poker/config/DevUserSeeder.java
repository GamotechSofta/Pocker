package com.poker.config;

import com.poker.entity.User;
import com.poker.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@Profile("dev")
public class DevUserSeeder {

    private static final Logger log = LoggerFactory.getLogger(DevUserSeeder.class);
    private static final String DEV_PASSWORD = "password";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public DevUserSeeder(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void seedDevelopmentUsers() {
        for (int i = 1; i <= 6; i++) {
            String username = "player" + i;
            if (userRepository.existsByUsernameIgnoreCase(username)) {
                continue;
            }
            userRepository.save(new User(
                    username,
                    username + "@poker.local",
                    passwordEncoder.encode(DEV_PASSWORD)
            ));
            log.info("Seeded development user {}", username);
        }
    }
}
