package com.poker.bot;

public final class BotIdentity {

    public static final String USERNAME_PREFIX = "bot_";
    public static final String EMAIL_SUFFIX = "@bot.local";

    private BotIdentity() {
    }

    public static boolean isBotUsername(String username) {
        return username != null && username.toLowerCase().startsWith(USERNAME_PREFIX);
    }

    public static boolean isBotEmail(String email) {
        return email != null && email.toLowerCase().endsWith(EMAIL_SUFFIX);
    }

    public static String displayName(String username) {
        if (!isBotUsername(username)) {
            return username;
        }
        String rest = username.substring(USERNAME_PREFIX.length()).replace('_', ' ').trim();
        if (rest.isEmpty()) {
            return "Bot";
        }
        return "Bot " + Character.toUpperCase(rest.charAt(0)) + rest.substring(1);
    }
}
