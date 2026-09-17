package com.petrolpump.discount.config;

import com.petrolpump.discount.domain.*;
import com.petrolpump.discount.repo.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SeedData {
    @Bean
    CommandLineRunner seed(PumpRepository pumps, LoyaltyConfigRepository configs) {
        return args -> {
            if (configs.findById(1L).isEmpty()) {
                configs.save(new LoyaltyConfig());
            } else {
                LoyaltyConfig cfg = configs.findById(1L).orElseThrow();
                // Backfill new 0–100 L tier once (default 10 paise/coin per litre).
                if (cfg.getRate0to100() <= 0) {
                    cfg.setRate0to100(10);
                    configs.save(cfg);
                }
            }
            // Seed once only — geo/rates/station details are admin-editable in DB afterwards.
            if (pumps.count() == 0) {
                Pump p = new Pump();
                p.setName("Nagashree Service Station");
                p.setLat(13.7652412);
                p.setLng(76.8516552);
                p.setRadiusMeters(50);
                p.setRedeemToken(java.util.UUID.randomUUID().toString().replace("-", ""));
                p.setAddress("Ground Floor, NH 4, near Anjaniya Swami Temple, Mangangi Thanda, Tumkur, Karnataka 572139");
                p.setContactName("Dhanush R");
                p.setContactPhone("9448166221");
                p.setMapsUrl("https://maps.app.goo.gl/NWSYMhsgTPrDCrKs6");
                pumps.save(p);
            } else {
                // Backfill station contact once if empty (does not overwrite geo).
                Pump p = pumps.findAll().get(0);
                boolean dirty = false;
                if (p.getAddress() == null || p.getAddress().isBlank()) {
                    p.setAddress("Ground Floor, NH 4, near Anjaniya Swami Temple, Mangangi Thanda, Tumkur, Karnataka 572139");
                    dirty = true;
                }
                if (p.getContactName() == null || p.getContactName().isBlank()) {
                    p.setContactName("Dhanush R");
                    dirty = true;
                }
                if (p.getContactPhone() == null || p.getContactPhone().isBlank()
                        || "9558166221".equals(p.getContactPhone().replaceAll("\\D", ""))) {
                    p.setContactPhone("9448166221");
                    dirty = true;
                }
                if (p.getMapsUrl() == null || p.getMapsUrl().isBlank()) {
                    p.setMapsUrl("https://maps.app.goo.gl/NWSYMhsgTPrDCrKs6");
                    dirty = true;
                }
                if ("pump-demo-token".equals(p.getRedeemToken())) {
                    p.setRedeemToken(java.util.UUID.randomUUID().toString().replace("-", ""));
                    dirty = true;
                }
                if (dirty) pumps.save(p);
            }
        };
    }
}
