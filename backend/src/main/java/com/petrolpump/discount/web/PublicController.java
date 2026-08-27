package com.petrolpump.discount.web;

import com.petrolpump.discount.domain.LoyaltyConfig;
import com.petrolpump.discount.domain.Pump;
import com.petrolpump.discount.repo.LoyaltyConfigRepository;
import com.petrolpump.discount.repo.PumpRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/public")
public class PublicController {
    private final PumpRepository pumps;
    private final LoyaltyConfigRepository configs;

    public PublicController(PumpRepository pumps, LoyaltyConfigRepository configs) {
        this.pumps = pumps;
        this.configs = configs;
    }

    @GetMapping("/station")
    public Map<String, Object> station() {
        Pump p = pumps.findAll().stream().findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Pump not configured"));
        LoyaltyConfig cfg = configs.findById(1L).orElseGet(LoyaltyConfig::new);
        Map<String, Object> rates = new LinkedHashMap<>();
        rates.put("rate0to100", cfg.getRate0to100());
        rates.put("rate100to200", cfg.getRate100to200());
        rates.put("rate200to300", cfg.getRate200to300());
        rates.put("rate300plus", cfg.getRate300plus());
        rates.put("bonusMidPct", cfg.getBonusMidPct());
        rates.put("bonusHighPct", cfg.getBonusHighPct());
        rates.put("thresholdMidLitres", cfg.getThresholdMidLitres());
        rates.put("thresholdHighLitres", cfg.getThresholdHighLitres());
        rates.put("unit", "paise_per_litre");
        rates.put("note", "1 coin = 1 paisa.");

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("name", p.getName());
        out.put("address", nullToEmpty(p.getAddress()));
        out.put("contactName", nullToEmpty(p.getContactName()));
        out.put("contactPhone", nullToEmpty(p.getContactPhone()));
        out.put("mapsUrl", nullToEmpty(p.getMapsUrl()));
        out.put("lat", p.getLat());
        out.put("lng", p.getLng());
        out.put("radiusMeters", p.getRadiusMeters());
        out.put("rates", rates);
        return out;
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
