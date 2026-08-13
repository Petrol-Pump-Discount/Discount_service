package com.petrolpump.discount.service;

public class BillOcrResult {
    private boolean duplicate;
    private String billNo;
    private String fccId;
    private String transId;
    private String vehicleNo;
    private Double volumeLitres;
    private Double saleAmount;
    private String fuel;
    private String rawText;

    public boolean isDuplicate() { return duplicate; }
    public void setDuplicate(boolean duplicate) { this.duplicate = duplicate; }
    public String getBillNo() { return billNo; }
    public void setBillNo(String billNo) { this.billNo = billNo; }
    public String getFccId() { return fccId; }
    public void setFccId(String fccId) { this.fccId = fccId; }
    public String getTransId() { return transId; }
    public void setTransId(String transId) { this.transId = transId; }
    public String getVehicleNo() { return vehicleNo; }
    public void setVehicleNo(String vehicleNo) { this.vehicleNo = vehicleNo; }
    public Double getVolumeLitres() { return volumeLitres; }
    public void setVolumeLitres(Double volumeLitres) { this.volumeLitres = volumeLitres; }
    public Double getSaleAmount() { return saleAmount; }
    public void setSaleAmount(Double saleAmount) { this.saleAmount = saleAmount; }
    public String getFuel() { return fuel; }
    public void setFuel(String fuel) { this.fuel = fuel; }
    public String getRawText() { return rawText; }
    public void setRawText(String rawText) { this.rawText = rawText; }

    /** Primary key for storage: prefer FCC ID, else Trans ID. */
    public String receiptKey() {
        java.util.List<String> keys = candidateReceiptKeys();
        return keys.isEmpty() ? null : keys.get(0);
    }

    /** All normalized ID keys from the bill — used to catch OCR picking FCC vs Trans on re-upload. */
    public java.util.List<String> candidateReceiptKeys() {
        java.util.LinkedHashSet<String> out = new java.util.LinkedHashSet<>();
        for (String src : new String[]{fccId, transId}) {
            String k = normalizeReceiptDigits(src);
            if (k != null) out.add(k);
        }
        return new java.util.ArrayList<>(out);
    }

    public static String normalizeBillNo(String billNo) {
        if (billNo == null) return null;
        String n = billNo.trim().replaceAll("\\s+", "").toUpperCase(java.util.Locale.ROOT);
        return n.isEmpty() ? null : n;
    }

    public static String normalizeReceiptDigits(String src) {
        if (src == null || src.isBlank()) return null;
        String d = src.replaceAll("\\D", "");
        if (d.isEmpty()) return null;
        if (d.length() > 9) d = d.substring(d.length() - 9);
        if (d.length() < 9) d = String.format("%9s", d).replace(' ', '0');
        return d;
    }
}
