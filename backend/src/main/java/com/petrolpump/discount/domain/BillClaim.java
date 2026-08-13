package com.petrolpump.discount.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "bill_claims", uniqueConstraints = @UniqueConstraint(columnNames = {"receiptKey"}))
public class BillClaim {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false) private AppUser user;
    @ManyToOne(optional = false) private Pump pump;
    @Column(nullable = false, length = 32) private String receiptKey;
    @Column(nullable = false, length = 20) private String vehicleNo;
    private double volumeLitres;
    private Double saleAmount;
    private String billNo;
    @Column(length = 32) private String fccId;
    @Column(length = 32) private String transId;
    private String imagePath;
    private Double claimLat;
    private Double claimLng;
    private Double distanceMeters;
    @Enumerated(EnumType.STRING) @Column(nullable = false)
    private ClaimStatus status = ClaimStatus.QUEUED;
    private String rejectReason;
    private long coinsCredited;
    private Instant billTime;
    @Column(nullable = false) private Instant createdAt = Instant.now();
    private Instant decidedAt;

    public Long getId() { return id; }
    public AppUser getUser() { return user; }
    public void setUser(AppUser user) { this.user = user; }
    public Pump getPump() { return pump; }
    public void setPump(Pump pump) { this.pump = pump; }
    public String getReceiptKey() { return receiptKey; }
    public void setReceiptKey(String receiptKey) { this.receiptKey = receiptKey; }
    public String getVehicleNo() { return vehicleNo; }
    public void setVehicleNo(String vehicleNo) { this.vehicleNo = vehicleNo; }
    public double getVolumeLitres() { return volumeLitres; }
    public void setVolumeLitres(double volumeLitres) { this.volumeLitres = volumeLitres; }
    public Double getSaleAmount() { return saleAmount; }
    public void setSaleAmount(Double saleAmount) { this.saleAmount = saleAmount; }
    public String getBillNo() { return billNo; }
    public void setBillNo(String billNo) { this.billNo = billNo; }
    public String getFccId() { return fccId; }
    public void setFccId(String fccId) { this.fccId = fccId; }
    public String getTransId() { return transId; }
    public void setTransId(String transId) { this.transId = transId; }
    public String getImagePath() { return imagePath; }
    public void setImagePath(String imagePath) { this.imagePath = imagePath; }
    public Double getClaimLat() { return claimLat; }
    public void setClaimLat(Double claimLat) { this.claimLat = claimLat; }
    public Double getClaimLng() { return claimLng; }
    public void setClaimLng(Double claimLng) { this.claimLng = claimLng; }
    public Double getDistanceMeters() { return distanceMeters; }
    public void setDistanceMeters(Double distanceMeters) { this.distanceMeters = distanceMeters; }
    public ClaimStatus getStatus() { return status; }
    public void setStatus(ClaimStatus status) { this.status = status; }
    public String getRejectReason() { return rejectReason; }
    public void setRejectReason(String rejectReason) { this.rejectReason = rejectReason; }
    public long getCoinsCredited() { return coinsCredited; }
    public void setCoinsCredited(long coinsCredited) { this.coinsCredited = coinsCredited; }
    public Instant getBillTime() { return billTime; }
    public void setBillTime(Instant billTime) { this.billTime = billTime; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getDecidedAt() { return decidedAt; }
    public void setDecidedAt(Instant decidedAt) { this.decidedAt = decidedAt; }
}
