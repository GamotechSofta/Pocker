package com.poker.config;

import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

import java.net.URI;
import java.util.Arrays;

final class AllowedOrigins {
    private AllowedOrigins() {}

    static String[] resolve(String configured, Environment environment) {
        boolean production = environment.acceptsProfiles(Profiles.of("prod", "staging"));
        return configured.isBlank() && !production
                ? new String[]{"http://localhost:*", "http://127.0.0.1:*"}
                : Arrays.stream(configured.split(",")).map(String::trim).filter(value -> !value.isEmpty())
                        .map(AllowedOrigins::exactOrigin).distinct().toArray(String[]::new);
    }

    private static String exactOrigin(String value) {
        var uri = URI.create(value);
        if (!("https".equals(uri.getScheme()) || "http".equals(uri.getScheme())) || uri.getHost() == null
                || !uri.getRawPath().isEmpty() || uri.getRawUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null) {
            throw new IllegalArgumentException("POKER_ALLOWED_ORIGINS must contain exact HTTP origins.");
        }
        return value;
    }
}
