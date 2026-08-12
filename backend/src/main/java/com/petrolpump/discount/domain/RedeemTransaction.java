package com.petrolpump.discount.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "redeem_transactions")
public class RedeemTransaction {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false) private AppUser user;
    @ManyToOne(optional = false) private Pump pump;
    @Column(nullable = false) private long coins;
    @Column(nullable = false) private long rupeesPaise; // coins == paise
    @Column(nullable = false) private Instant createdAt = Instant.now();
    @Column(nullable = false) private String businessDay; // yyyy-MM-dd of 6am window

    public Long getId() { return id; }
    public AppUser getUser() { return user; }
    public void setUser(AppUser user) { this.user = user; }
    public Pump getPump() { return pump; }
    public void setPump(Pump pump) { this.pump = pump; }
    public long getCoins() { return coins; }
    public void setCoins(long coins) { this.coins = coins; }
    public long getRupeesPaise() { return rupeesPaise; }
    public void setRupeesPaise(long rupeesPaise) { this.rupeesPaise = rupeesPaise; }
    public Instant getCreatedAt() { return createdAt; }
    public String getBusinessDay() { return businessDay; }
    public void setBusinessDay(String businessDay) { this.businessDay = businessDay; }
}
