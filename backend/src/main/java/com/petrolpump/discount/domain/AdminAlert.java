package com.petrolpump.discount.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "admin_alerts")
public class AdminAlert {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false) private String type;
    @Column(nullable = false, length = 2000) private String message;
    private String phone;
    private boolean readFlag = false;
    private Instant createdAt = Instant.now();

    public Long getId() { return id; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public boolean isReadFlag() { return readFlag; }
    public void setReadFlag(boolean readFlag) { this.readFlag = readFlag; }
    public Instant getCreatedAt() { return createdAt; }
}
