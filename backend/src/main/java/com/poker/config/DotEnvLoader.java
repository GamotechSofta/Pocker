package com.poker.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Loads {@code .env} into JVM system properties before Spring boots.
 * Does not override real OS environment variables or existing system properties.
 */
public final class DotEnvLoader {

    private DotEnvLoader() {
    }

    public static void load() {
        Path file = resolveEnvFile();
        if (file == null) {
            return;
        }
        try {
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            for (String raw : lines) {
                String line = raw.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                int eq = line.indexOf('=');
                if (eq <= 0) {
                    continue;
                }
                String key = line.substring(0, eq).trim();
                String value = stripQuotes(line.substring(eq + 1).trim());
                if (key.isEmpty()) {
                    continue;
                }
                if (System.getenv(key) != null) {
                    continue;
                }
                if (System.getProperty(key) != null) {
                    continue;
                }
                System.setProperty(key, value);
            }
        } catch (IOException ignored) {
            // Fall back to defaults in application.yml
        }
    }

    private static Path resolveEnvFile() {
        Path cwd = Path.of(".env");
        if (Files.isRegularFile(cwd)) {
            return cwd.toAbsolutePath().normalize();
        }
        Path nested = Path.of("backend", ".env");
        if (Files.isRegularFile(nested)) {
            return nested.toAbsolutePath().normalize();
        }
        return null;
    }

    private static String stripQuotes(String value) {
        if (value.length() >= 2) {
            char first = value.charAt(0);
            char last = value.charAt(value.length() - 1);
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                return value.substring(1, value.length() - 1);
            }
        }
        return value;
    }
}
