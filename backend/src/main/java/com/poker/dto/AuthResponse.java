package com.poker.dto;

public record AuthResponse(
        String token,
        String tokenType,
        long expiresIn,
        UserResponse user,
        LaunchMetadata launch
) {
    public AuthResponse(String token, String tokenType, long expiresIn, UserResponse user) {
        this(token, tokenType, expiresIn, user, null);
    }
}
