package com.petrolpump.discount.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
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
    void extractsSiteOmatPreAuthAndContinuationTransactionIds() {
        String text = """
                SiteOmat - Transaction Report
                Ser. Receipt nt DU DU Global Vehicle Vehicle Preset Preset Transaction Sale Start
                No. No. Product Amount(Rs.) Volume(Ltr) Unit Method
                High
                 1  300000158 Speed  1000.00  10.020  99.79  Cash  13/08/26  08:43:29   4  3  12  PreAuth   Money
                Diesel (Rs.)  1000.00  300000158  1000.00  13/08/26  08:42:35  'AA'  5071894.540  5071904.560  O
                High
                 35  300000260 Speed  2282.20  22.870  99.79  Cash  13/08/26  15:09:09   7  5  20  PreAuth     300000261  2282.20  13/08/26  15:06:06  'AA'  1059806.320  1059829.190  O
                Diesel
                High
                Diesel (Rs.)  1501.00  300000265  1501.00  13/08/26
                """;
        Set<String> ids = PdfMatchService.extractTransactionIds(text);
        assertTrue(ids.contains("300000158"), ids.toString());
        assertTrue(ids.contains("300000261"), ids.toString());
        assertTrue(ids.contains("300000265"), ids.toString());
        // Receipt No that is not also Transaction ID should not be required — 300000260 is receipt for row 35
        // (may or may not appear; we only assert Transaction IDs)
        assertFalse(ids.contains("5071894"), "totalizer must not match");
    }

    @Test
    void extractsFromRealSiteOmatPdfIfPresent() throws Exception {
        Path pdf = Path.of(System.getProperty("user.home"), "Downloads", "SiteOmat - Transaction Report.pdf");
        if (!Files.isRegularFile(pdf)) {
            return; // optional local fixture
        }
        String text;
        try (PDDocument doc = Loader.loadPDF(Files.readAllBytes(pdf))) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            text = stripper.getText(doc);
        }
        Set<String> ids = PdfMatchService.extractTransactionIds(text);
        assertTrue(ids.contains("300000261"), "missing 300000261 in " + ids);
        assertTrue(ids.contains("300000265"), "missing 300000265 in " + ids);
    }
}
