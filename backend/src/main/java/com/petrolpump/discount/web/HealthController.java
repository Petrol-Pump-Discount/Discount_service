package com.petrolpump.discount.web;

import com.petrolpump.discount.service.GeminiBillOcrService;
import com.petrolpump.discount.service.LocalReceiptOcrService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class HealthController {
    private final GeminiBillOcrService gemini;
    private final LocalReceiptOcrService local;
    private final String ocrMode;

    public HealthController(
            GeminiBillOcrService gemini,
            LocalReceiptOcrService local,
            @Value("${app.ocr.mode:auto}") String ocrMode) {
        this.gemini = gemini;
        this.local = local;
        this.ocrMode = ocrMode;
    }

    @GetMapping("/api/health")
    public Map<String, Object> health() {
        return Map.of(
                "status", "ok",
                "ocrMode", ocrMode,
                "geminiConfigured", gemini.isConfigured(),
                "localOcrAvailable", local.isAvailable()
        );
    }
}
