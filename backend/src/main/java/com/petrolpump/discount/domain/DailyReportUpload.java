package com.petrolpump.discount.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "daily_report_uploads")
public class DailyReportUpload {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String fileName;
    @Lob private String receiptKeysCsv;
    private int matchedCount;
    private int rejectedCount;
    private Instant createdAt = Instant.now();

    public Long getId() { return id; }
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public String getReceiptKeysCsv() { return receiptKeysCsv; }
    public void setReceiptKeysCsv(String receiptKeysCsv) { this.receiptKeysCsv = receiptKeysCsv; }
    public int getMatchedCount() { return matchedCount; }
    public void setMatchedCount(int matchedCount) { this.matchedCount = matchedCount; }
    public int getRejectedCount() { return rejectedCount; }
    public void setRejectedCount(int rejectedCount) { this.rejectedCount = rejectedCount; }
    public Instant getCreatedAt() { return createdAt; }
}
