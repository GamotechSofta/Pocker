package com.poker.dto;

import com.poker.operations.OperatorSession;
import java.math.BigDecimal;

/** Browser metadata; never contains a provider token or internal service key. */
public record LaunchMetadata(
        String mode, String gameCode, String username, String userId,
        String currency, String lang, String extsess, String closeUrl, BigDecimal balance
) {
    public static LaunchMetadata from(OperatorSession session, BigDecimal balance) {
        return new LaunchMetadata("operator", session.getGameCode(), session.getUsername(),
                session.getUserId(), session.getCurrency(), session.getLang(),
                session.getExtsess(), session.getCloseUrl(), balance);
    }
}
