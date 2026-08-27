package com.petrolpump.discount.service;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class PdfMatchServiceTest {

    @Test
    void extractsTransactionIdColumnOnly() {
        String text = """
                SlNo Product Transaction ID Amount Nozzle
                1 PETROL 001234567 1520.00 2
                2 DIESEL 987654321 2000.50 1
                3 XP95 555666777 999.00 3
                """;
        Set<String> ids = PdfMatchService.extractTransactionIds(text);
        assertEquals(Set.of("001234567", "987654321", "555666777"), ids);
        assertFalse(ids.contains("000001520"));
    }

    @Test
    void extractsLabeledTransactionId() {
        String text = "Foo bar\nTransaction ID: 123456789\nOther 999888777 noise\nTrns.ID 222333444\n";
        Set<String> ids = PdfMatchService.extractTransactionIds(text);
        assertTrue(ids.contains("123456789"));
        assertTrue(ids.contains("222333444"));
        assertFalse(ids.contains("999888777"));
    }

    @Test
    void ignoresBareNineDigitNumbersWithoutTxnMarkers() {
        String text = "Daily report\n999888777\n111222333\nNozzle 4\n";
        Set<String> ids = PdfMatchService.extractTransactionIds(text);
        assertTrue(ids.isEmpty());
    }

    @Test
    void extractsTxnColumnFromRealSiteOmatPdfIfPresent() throws Exception {
        Path pdf = Path.of(System.getProperty("user.home"), "Downloads", "SiteOmat - Transaction Report (1).pdf");
        if (!Files.isRegularFile(pdf)) {
            pdf = Path.of(System.getProperty("user.home"), "Downloads", "SiteOmat - Transaction Report.pdf");
        }
        if (!Files.isRegularFile(pdf)) {
            return;
        }
        Set<String> ids = PdfMatchService.extractTransactionIdsFromPdf(Files.readAllBytes(pdf));
        assertTrue(ids.size() >= 100, "expected many txn ids, got " + ids.size());
        assertTrue(ids.contains("300004636"), "Transaction ID column must include 300004636");
        assertTrue(ids.contains("300003920"), "shifted txn column (empty preset) must include 300003920");
        // Receipt-only numbers at left must not be required; but 636 is in txn col
    }
}
