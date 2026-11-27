package com.poker.operations;

import java.math.BigDecimal;
import java.util.Locale;

/** Operations wallet transfer modes shared across all poker variants. */
public enum OperatorGameType {
    SPIN("spin", 1),
    REFUND("refund", 2);

    private final String wireName;
    private final int transferType;

    OperatorGameType(String wireName, int transferType) {
        this.wireName = wireName;
        this.transferType = transferType;
    }

    public String wireName() {
        return wireName;
    }

    public int transferType() {
        return transferType;
    }

    public static OperatorGameType from(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Wallet game_type is required.");
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        for (OperatorGameType value : values()) {
            if (value.wireName.equals(normalized)) {
                return value;
            }
        }
        throw new IllegalArgumentException("Unsupported wallet game_type: " + raw);
    }

    public void validate(BigDecimal bet, BigDecimal win, int suppliedTransferType) {
        if (suppliedTransferType != transferType) {
            throw new IllegalArgumentException("Invalid transfer type " + suppliedTransferType + " for " + wireName);
        }
        if (bet == null || win == null || bet.signum() < 0 || win.signum() < 0) {
            throw new IllegalArgumentException("Wallet amounts must be non-negative.");
        }
        if (this == REFUND) {
            if (bet.signum() <= 0 || win.signum() != 0) {
                throw new IllegalArgumentException("Refund transfer requires a positive bet amount and zero win.");
            }
            return;
        }
        if (bet.signum() == 0 && win.signum() == 0) {
            throw new IllegalArgumentException("Wallet transfer requires a positive bet or win amount.");
        }
    }
}
