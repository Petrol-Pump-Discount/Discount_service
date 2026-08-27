package com.petrolpump.discount.service;

import com.petrolpump.discount.domain.LoyaltyConfig;

public final class CoinCalculator {
    private CoinCalculator() {}

    public static long baseCoins(double volumeLitres, LoyaltyConfig cfg) {
        if (volumeLitres <= 0) return 0;
        int rate;
        if (volumeLitres < 100) rate = cfg.getRate0to100();
        else if (volumeLitres < 200) rate = cfg.getRate100to200();
        else if (volumeLitres < 300) rate = cfg.getRate200to300();
        else rate = cfg.getRate300plus();
        return (long) Math.floor(rate * volumeLitres);
    }

    public static long withBonus(long base, double prior30dLitres, LoyaltyConfig cfg) {
        if (base <= 0) return 0;
        int pct = 0;
        if (prior30dLitres >= cfg.getThresholdHighLitres()) pct = cfg.getBonusHighPct();
        else if (prior30dLitres >= cfg.getThresholdMidLitres()) pct = cfg.getBonusMidPct();
        return (long) Math.floor(base * (1.0 + pct / 100.0));
    }
}
