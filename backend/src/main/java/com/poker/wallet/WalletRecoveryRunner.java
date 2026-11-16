package com.poker.wallet;

import com.poker.service.TableService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class WalletRecoveryRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(WalletRecoveryRunner.class);

    private final TableService tableService;

    public WalletRecoveryRunner(TableService tableService) {
        this.tableService = tableService;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            int refunded = tableService.recoverOrphanEscrows();
            if (refunded > 0) {
                log.info("[Wallet] Startup recovered {} orphan escrow(s)", refunded);
            }
        } catch (RuntimeException ex) {
            log.warn("[Wallet] Orphan escrow recovery skipped: {}", ex.getMessage());
        }
    }
}
