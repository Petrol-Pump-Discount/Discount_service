package com.petrolpump.discount.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "loyalty_config")
public class LoyaltyConfig {
    @Id
    private Long id = 1L;
    @Column(nullable = false, columnDefinition = "integer default 10")
    private int rate0to100 = 10;
    private int rate100to200 = 20;
    private int rate200to300 = 40;
    private int rate300plus = 90;
    private int bonusMidPct = 10;
    private int bonusHighPct = 20;
    private int thresholdMidLitres = 2000;
    private int thresholdHighLitres = 5000;
    private int autoRejectDays = 3;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public int getRate0to100() { return rate0to100; }
    public void setRate0to100(int v) { this.rate0to100 = v; }
    public int getRate100to200() { return rate100to200; }
    public void setRate100to200(int v) { this.rate100to200 = v; }
    public int getRate200to300() { return rate200to300; }
    public void setRate200to300(int v) { this.rate200to300 = v; }
    public int getRate300plus() { return rate300plus; }
    public void setRate300plus(int v) { this.rate300plus = v; }
    public int getBonusMidPct() { return bonusMidPct; }
    public void setBonusMidPct(int v) { this.bonusMidPct = v; }
    public int getBonusHighPct() { return bonusHighPct; }
    public void setBonusHighPct(int v) { this.bonusHighPct = v; }
    public int getThresholdMidLitres() { return thresholdMidLitres; }
    public void setThresholdMidLitres(int v) { this.thresholdMidLitres = v; }
    public int getThresholdHighLitres() { return thresholdHighLitres; }
    public void setThresholdHighLitres(int v) { this.thresholdHighLitres = v; }
    public int getAutoRejectDays() { return autoRejectDays; }
    public void setAutoRejectDays(int v) { this.autoRejectDays = v; }
}
