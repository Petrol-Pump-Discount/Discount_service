package com.petrolpump.discount.service;

import org.junit.jupiter.api.Test;

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
        assertFalse(ids.contains("000001520")); // amount digits must not be scooped
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
    void ignoresBareNineDigitNumbersWithoutTxnHeader() {
        String text = "Daily report\n999888777\n111222333\nNozzle 4\n";
        Set<String> ids = PdfMatchService.extractTransactionIds(text);
        assertTrue(ids.isEmpty());
    }
}
