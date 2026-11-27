package com.poker.wallet;

import com.poker.config.WalletProperties;
import com.poker.exception.ApiException;
import com.poker.operations.OperatorSession;
import com.poker.operations.OperatorSessionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WalletControllerOperatorTest {
    @Test void blocksOperatorAddCashBeforeWalletMutation() {
        WalletService wallets = mock(WalletService.class);
        OperatorSessionRepository sessions = mock(OperatorSessionRepository.class);
        Jwt jwt = mock(Jwt.class);
        when(jwt.getSubject()).thenReturn("player");
        when(sessions.findByPlayerId("player")).thenReturn(Optional.of(new OperatorSession("player", "token", "P", "1001", null)));
        var controller = new WalletController(wallets, new WalletProperties(), sessions);
        assertEquals(403, assertThrows(ApiException.class, () -> controller.addCash(jwt, Map.of("amount", 1000))).getStatus().value());
        verifyNoInteractions(wallets);
    }
    @Test void refreshesBalanceBeforeReadingPoolsAndReturnsCurrency() {
        WalletService wallets = mock(WalletService.class);
        OperatorSessionRepository sessions = mock(OperatorSessionRepository.class);
        Jwt jwt = mock(Jwt.class);
        when(jwt.getSubject()).thenReturn("player");
        when(sessions.findByPlayerId("player")).thenReturn(Optional.of(new OperatorSession("player", "token", "P", "1001", "razz", "Alex", null, "USD", "en", null, null)));
        WalletAccount account = new WalletAccount("player");
        account.setDepositBalance(new BigDecimal("42.25"));
        when(wallets.balanceOf("player")).thenReturn(new BigDecimal("42.25"));
        when(wallets.getOrCreateWallet("player")).thenReturn(account);
        var body = new WalletController(wallets, new WalletProperties(), sessions).getBalance(jwt).getBody();
        assertNotNull(body);
        assertEquals(new BigDecimal("42.25"), body.get("balance"));
        assertEquals(new BigDecimal("42.25"), body.get("depositBalance"));
        assertEquals("USD", body.get("currency"));
        assertEquals(true, body.get("readOnly"));
        assertEquals(0L, body.get("addCashAmount"));
        var order = inOrder(wallets);
        order.verify(wallets).balanceOf("player");
        order.verify(wallets).getOrCreateWallet("player");
    }
}
