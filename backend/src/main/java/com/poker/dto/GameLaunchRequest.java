package com.poker.dto;

import jakarta.validation.constraints.NotBlank;

public record GameLaunchRequest(
        @NotBlank String token,
        @NotBlank String partnerCode,
        @NotBlank String gid,
        @NotBlank String username,
        String userId,
        String currency,
        String lang,
        String extsess,
        String closeUrl
) { }
