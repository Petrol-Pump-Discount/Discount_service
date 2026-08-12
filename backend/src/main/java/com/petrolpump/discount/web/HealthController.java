package com.petrolpump.discount.web;

import com.petrolpump.discount.service.GeminiBillOcrService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class HealthController {
    private final GeminiBillOcrService ocr;

    public HealthController(GeminiBillOcrService ocr) {
        this.ocr = ocr;
    }

    @GetMapping("/api/health")
    public Map<String, Object> health() {
        return Map.of(
                "status", "ok",
                "geminiConfigured", ocr.isConfigured()
        );
    }
}
