package com.petrolpump.discount.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "wallet_adjustments")
public class WalletAdjustment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    private AppUser user;

    /** Admin who made the change (optional if system). */
    @ManyToOne
    private AppUser admin;

    /** Positive = credit, negative = debit (coins / paise). */
    @Column(nullable = false)
    private long deltaCoins;

    @Column(nullable = false)
    private long balanceAfter;

    @Column(nullable = false, length = 280)
    private String reason = "";

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    public Long getId() { return id; }
    public AppUser getUser() { return user; }
    public void setUser(AppUser user) { this.user = user; }
    public AppUser getAdmin() { return admin; }
    public void setAdmin(AppUser admin) { this.admin = admin; }
    public long getDeltaCoins() { return deltaCoins; }
    public void setDeltaCoins(long deltaCoins) { this.deltaCoins = deltaCoins; }
    public long getBalanceAfter() { return balanceAfter; }
    public void setBalanceAfter(long balanceAfter) { this.balanceAfter = balanceAfter; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public Instant getCreatedAt() { return createdAt; }
}
