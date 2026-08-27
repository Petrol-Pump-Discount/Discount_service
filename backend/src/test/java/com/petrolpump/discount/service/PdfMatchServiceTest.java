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
        assertTrue(ids.contains("300000260"), "receipt no should match: " + ids);
        assertFalse(ids.contains("5071894"), "totalizer must not match");
    }

    @Test
    void extractsTxnFromMoneyContinuationAndReceiptNo() {
        // Real extract (sortByPosition=false): txn 300004636 is on Money/(Rs.) continuation
        String text = """
                 255  300004636
                High
                Speed
                Diesel
                 500.00  5.010  99.79  Cash  27/08/26  09:54:25   7  5  20  PreAuth     300004635  500.00
                 256  300004637
                High
                Speed
                Diesel
                 19501.96  195.430  99.79  Cash  27/08/26  09:56:09   7  6  21  PreAuth   Money
                (Rs.)  25000.00  300004636  19501.96  27/08/26  09:51:05
                """;
        Set<String> ids = PdfMatchService.extractTransactionIds(text);
        assertTrue(ids.contains("300004636"), "txn/receipt: " + ids);
        assertTrue(ids.contains("300004637"), "receipt: " + ids);
    }

    @Test
    void extractsFromRealSiteOmatPdfIfPresent() throws Exception {
        Path pdf = Path.of(System.getProperty("user.home"), "Downloads", "SiteOmat - Transaction Report (1).pdf");
        if (!Files.isRegularFile(pdf)) {
            pdf = Path.of(System.getProperty("user.home"), "Downloads", "SiteOmat - Transaction Report.pdf");
        }
        if (!Files.isRegularFile(pdf)) {
            return;
        }
        String text = PdfMatchService.extractPdfText(Files.readAllBytes(pdf));
        Set<String> ids = PdfMatchService.extractTransactionIds(text);
        assertTrue(ids.contains("300004636"),
                "300004636 must be parsed as txn/receipt; got " + ids.size() + " ids");
    }
}
