package com.poker.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import com.fasterxml.jackson.annotation.JsonAlias;

public record GameLaunchRequest(
        @NotBlank @Size(max = 2048) String token,
        @NotBlank @Size(max = 128) @JsonAlias("partner_code") String partnerCode,
        @NotBlank @Size(max = 128) String gid,
        @NotBlank @Size(max = 128) @JsonAlias("game_code") String gameCode,
        @NotBlank @Size(max = 128) String username,
        @Size(max = 256) @JsonAlias("user_id") String userId,
        @Size(max = 16) String currency,
        @Size(max = 35) String lang,
        @Size(max = 512) String extsess,
        @Size(max = 2048) @JsonAlias("close_url") String closeUrl
) { }
