package com.poker.wallet;

import com.poker.PokerBackendApplication;
import com.poker.support.AbstractMongoTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(classes = PokerBackendApplication.class)
class WalletServiceTest extends AbstractMongoTest {

    @Autowired
    WalletService walletService;

    @Test
    void startingBalanceIsGrantedOnce() {
        String playerId = "p_" + UUID.randomUUID();
        assertEquals(0, BigDecimal.valueOf(10000).compareTo(walletService.balanceOf(playerId)));
        walletService.getOrCreateWallet(playerId);
        assertEquals(0, BigDecimal.valueOf(10000).compareTo(walletService.balanceOf(playerId)));
    }

    @Test
    void debitAndCreditAreIdempotent() {
        String playerId = "p_" + UUID.randomUUID();
        walletService.getOrCreateWallet(playerId);
        String stakeKey = "STAKE_" + UUID.randomUUID();
        walletService.debit(
                playerId,
                BigDecimal.valueOf(1000),
                WalletService.TX_GAME_ENTRY_STAKE,
                stakeKey,
                null,
                "buy-in",
                Map.of()
        );
        walletService.debit(
                playerId,
                BigDecimal.valueOf(1000),
                WalletService.TX_GAME_ENTRY_STAKE,
                stakeKey,
                null,
                "buy-in",
                Map.of()
        );
        assertEquals(0, BigDecimal.valueOf(9000).compareTo(walletService.balanceOf(playerId)));

        walletService.credit(
                playerId,
                BigDecimal.valueOf(1000),
                WalletService.TX_GAME_ENTRY_REFUND,
                "REFUND_" + stakeKey,
                null,
                "refund",
                Map.of()
        );
        assertEquals(0, BigDecimal.valueOf(10000).compareTo(walletService.balanceOf(playerId)));
    }

    @Test
    void insufficientBalanceThrows() {
        String playerId = "p_" + UUID.randomUUID();
        walletService.getOrCreateWallet(playerId);
        assertThrows(InsufficientBalanceException.class, () ->
                walletService.debit(
                        playerId,
                        BigDecimal.valueOf(50_000),
                        WalletService.TX_GAME_ENTRY_STAKE,
                        "STAKE_" + UUID.randomUUID(),
                        null,
                        "buy-in",
                        Map.of()
                )
        );
    }

    @Test
    void transactionHistoryIncludesStartingGrant() {
        String playerId = "p_" + UUID.randomUUID();
        walletService.getOrCreateWallet(playerId);
        List<WalletTransaction> txs = walletService.getTransactions(playerId);
        assertTrue(txs.stream().anyMatch(t -> ("STARTING_" + playerId).equals(t.getIdempotencyKey())));
    }
}
