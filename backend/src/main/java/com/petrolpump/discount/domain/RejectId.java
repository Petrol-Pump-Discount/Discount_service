package com.petrolpump.discount.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "reject_ids", uniqueConstraints = @UniqueConstraint(columnNames = {"receiptKey"}))
public class RejectId {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 32)
    private String receiptKey;
    private Instant createdAt = Instant.now();

    public Long getId() { return id; }
    public String getReceiptKey() { return receiptKey; }
    public void setReceiptKey(String receiptKey) { this.receiptKey = receiptKey; }
}
