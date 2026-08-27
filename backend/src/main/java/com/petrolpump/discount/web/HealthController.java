package com.petrolpump.discount.web;

import com.petrolpump.discount.service.GeminiBillOcrService;
import com.petrolpump.discount.service.GoogleVisionOcrService;
import com.petrolpump.discount.service.LocalReceiptOcrService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
public class HealthController {
    private final GeminiBillOcrService gemini;
    private final GoogleVisionOcrService vision;
    private final LocalReceiptOcrService local;
    private final String ocrMode;

    public HealthController(
            GeminiBillOcrService gemini,
            GoogleVisionOcrService vision,
            LocalReceiptOcrService local,
            @Value("${app.ocr.mode:auto}") String ocrMode) {
        this.gemini = gemini;
        this.vision = vision;
        this.local = local;
        this.ocrMode = ocrMode;
    }

    @GetMapping("/api/health")
    public Map<String, Object> health() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("status", "ok");
        m.put("ocrMode", ocrMode);
        m.put("geminiConfigured", gemini.isConfigured());
        m.put("geminiCircuitOpen", gemini.isCircuitOpen());
        m.put("visionConfigured", vision.isConfigured());
        m.put("localOcrAvailable", local.isAvailable());
        return m;
    }
}
