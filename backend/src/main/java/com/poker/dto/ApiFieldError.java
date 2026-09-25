package com.poker.dto;

public record ApiFieldError(
        String field,
        String message
) {
}
