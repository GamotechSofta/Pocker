package com.poker.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.wallet")
public class WalletProperties {

    /** Opening play-money grant for a brand-new wallet (mirrors Rummy mock-operator top-up). */
    private long startingBalance = 10_000;

    /** Default Add Cash pack size (INR / play chips). */
    private long addCashAmount = 1_000;

    /** Maximum single Add Cash top-up. */
    private long maxAddCashAmount = 50_000;

    /** Platform rake rate applied to pot when configured (cash-out path does not rake). */
    private double rakeRate = 0.15;

    public long getStartingBalance() {
        return startingBalance;
    }

    public void setStartingBalance(long startingBalance) {
        this.startingBalance = startingBalance;
    }

    public long getAddCashAmount() {
        return addCashAmount;
    }

    public void setAddCashAmount(long addCashAmount) {
        this.addCashAmount = addCashAmount;
    }

    public long getMaxAddCashAmount() {
        return maxAddCashAmount;
    }

    public void setMaxAddCashAmount(long maxAddCashAmount) {
        this.maxAddCashAmount = maxAddCashAmount;
    }

    public double getRakeRate() {
        return rakeRate;
    }

    public void setRakeRate(double rakeRate) {
        this.rakeRate = rakeRate;
    }
}
