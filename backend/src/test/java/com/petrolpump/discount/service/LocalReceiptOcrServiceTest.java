package com.petrolpump.discount.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LocalReceiptOcrServiceTest {

    @Test
    void parsesSiteOmatStyleReceipt() {
        String text = """
                IndianOil
                Nagashree Service Station
                FCC ID: 1234567890
                Trns.ID: 9876543210
                Bill No: AB12-345
                Vehicle No: KA51AH4570
                Volume: 12.450
                Amount: 1150.25
                Product: XTRAGREEN
                """;
        BillOcrResult r = LocalReceiptOcrService.parse(text);
        assertEquals("1234567890", r.getFccId());
        assertEquals("9876543210", r.getTransId());
        assertEquals("AB12-345", r.getBillNo());
        assertEquals("KA51AH4570", r.getVehicleNo());
        assertEquals(12.45, r.getVolumeLitres(), 0.001);
        assertEquals(1150.25, r.getSaleAmount(), 0.001);
        assertTrue(r.getFuel().contains("XTRAGREEN") || r.getFuel().equals("XTRAGREEN"));
        assertFalse(r.candidateReceiptKeys().isEmpty());
    }

    @Test
    void marksDuplicateReceipt() {
        String text = "Duplicate Receipt Copy\nFCC ID 111222333\nVolume 5.0\n";
        BillOcrResult r = LocalReceiptOcrService.parse(text);
        assertTrue(r.isDuplicate());
        assertEquals("111222333", r.getFccId());
        assertEquals(5.0, r.getVolumeLitres(), 0.001);
    }

    @Test
    void parsesVolumeOnNextLineAndLitreSuffix() {
        String text = """
                FCC ID 555666777
                Volume
                8.250
                Amount Rs. 800
                """;
        BillOcrResult r = LocalReceiptOcrService.parse(text);
        assertEquals("555666777", r.getFccId());
        assertEquals(8.25, r.getVolumeLitres(), 0.001);

        BillOcrResult r2 = LocalReceiptOcrService.parse("Trns.ID 111222333\n12.450 Ltrs\n");
        assertEquals("111222333", r2.getTransId());
        assertEquals(12.45, r2.getVolumeLitres(), 0.001);
    }
}
