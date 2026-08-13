package com.petrolpump.discount.config;

import com.petrolpump.discount.domain.*;
import com.petrolpump.discount.repo.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SeedData {
    @Bean
    CommandLineRunner seed(PumpRepository pumps, LoyaltyConfigRepository configs, AppUserRepository users,
                           @Value("${app.pump.lat:13.765987}") double pumpLat,
                           @Value("${app.pump.lng:76.852652}") double pumpLng,
                           @Value("${app.pump.radius-meters:50}") double radiusMeters) {
        return args -> {
            if (configs.findById(1L).isEmpty()) {
                configs.save(new LoyaltyConfig());
            }
            if (pumps.count() == 0) {
                Pump p = new Pump();
                p.setName("Nagashree Service Station");
                p.setLat(pumpLat);
                p.setLng(pumpLng);
                p.setRadiusMeters(radiusMeters);
                p.setRedeemToken("pump-demo-token");
                pumps.save(p);
            } else {
                Pump p = pumps.findAll().get(0);
                p.setLat(pumpLat);
                p.setLng(pumpLng);
                p.setRadiusMeters(radiusMeters);
                pumps.save(p);
            }
            users.findByPhone("9999999999").orElseGet(() -> {
                AppUser admin = new AppUser();
                admin.setPhone("9999999999");
                admin.setName("Admin");
                admin.setRole(UserRole.ADMIN);
                return users.save(admin);
            });
            users.findByPhone("8888888888").orElseGet(() -> {
                AppUser emp = new AppUser();
                emp.setPhone("8888888888");
                emp.setName("Employee");
                emp.setRole(UserRole.EMPLOYEE);
                return users.save(emp);
            });
        };
    }
}
