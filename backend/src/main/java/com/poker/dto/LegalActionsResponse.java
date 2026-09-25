package com.poker.dto;

import java.util.Set;

public record LegalActionsResponse(
        Set<String> actions,
        int callAmount,
        int minBetOrRaiseTo,
        int maxBetOrRaiseTo
) {
}
