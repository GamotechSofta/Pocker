package com.poker.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record GameActionRequest(
        @NotBlank String action,
        @Min(0) Integer amount,
        List<@Min(0) Integer> indices,
        String requestId,
        Long actionVersion
) {
    public GameActionRequest(String action, Integer amount, List<Integer> indices, String requestId) {
        this(action, amount, indices, requestId, null);
    }
}
