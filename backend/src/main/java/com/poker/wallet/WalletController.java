package com.poker.wallet;

import com.poker.config.WalletProperties;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Player wallet API (Rummy parity): balance + history. Add Cash tops up play money in-app
 * (no external operator cashier in this play-money build).
 */
@RestController
@RequestMapping("/api/wallet")
@Tag(name = "Wallet")
@SecurityRequirement(name = "bearer-jwt")
public class WalletController {

    private final WalletService walletService;
    private final WalletProperties walletProperties;

    public WalletController(WalletService walletService, WalletProperties walletProperties) {
        this.walletService = walletService;
        this.walletProperties = walletProperties;
    }

    @GetMapping("/balance")
    public ResponseEntity<Map<String, Object>> getBalance(@AuthenticationPrincipal Jwt jwt) {
        String owner = jwt.getSubject();
        WalletAccount account = walletService.getOrCreateWallet(owner);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("playerId", owner);
        body.put("balance", walletService.balanceOf(owner));
        body.put("depositBalance", account.getDepositBalance());
        body.put("winningsBalance", account.getWinningsBalance());
        body.put("bonusBalance", account.getBonusBalance());
        body.put("withdrawableBalance", account.getWinningsBalance());
        body.put("currency", WalletAccount.CURRENCY);
        body.put("addCashAmount", walletProperties.getAddCashAmount());
        body.put("cashierUrl", null);
        return ResponseEntity.ok(body);
    }

    @GetMapping("/transactions")
    public ResponseEntity<List<WalletTransaction>> getTransactions(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(walletService.getTransactions(jwt.getSubject()));
    }

    @PostMapping("/add-cash")
    public ResponseEntity<Map<String, Object>> addCash(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody(required = false) Map<String, Object> body
    ) {
        long amount = walletProperties.getAddCashAmount();
        if (body != null && body.get("amount") != null) {
            amount = ((Number) body.get("amount")).longValue();
        }
        WalletTransaction tx = walletService.addCash(jwt.getSubject(), amount);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("transaction", tx);
        response.put("balance", walletService.balanceOf(jwt.getSubject()));
        return ResponseEntity.ok(response);
    }

    @GetMapping("/platform-revenue")
    public ResponseEntity<Map<String, Object>> platformRevenue() {
        BigDecimal balance = walletService.balanceOf(WalletService.PLATFORM_TREASURY);
        List<WalletTransaction> recent = walletService.getTransactions(WalletService.PLATFORM_TREASURY);
        List<WalletTransaction> rake = recent.stream()
                .filter(t -> WalletService.TX_PLATFORM_RAKE.equals(t.getTransactionType()))
                .toList();
        BigDecimal recentRake = rake.stream()
                .map(WalletTransaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return ResponseEntity.ok(Map.of(
                "treasuryBalance", balance,
                "recentRakeCollected", recentRake,
                "rakeRate", walletProperties.getRakeRate(),
                "currency", WalletAccount.CURRENCY,
                "treasuryPlayerId", WalletService.PLATFORM_TREASURY,
                "recentRakeTransactions", rake.stream().limit(20).toList()
        ));
    }
}
