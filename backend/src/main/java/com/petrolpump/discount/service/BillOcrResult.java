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

    public String receiptKey() {
        String src = (fccId != null && !fccId.isBlank()) ? fccId : transId;
        if (src == null || src.isBlank()) {
            return null;
        }
        String d = src.replaceAll("\\D", "");
        if (d.length() > 9) {
            d = d.substring(d.length() - 9);
        }
        if (d.length() < 9) {
            d = String.format("%9s", d).replace(' ', '0');
        }
        return d;
    }
}
