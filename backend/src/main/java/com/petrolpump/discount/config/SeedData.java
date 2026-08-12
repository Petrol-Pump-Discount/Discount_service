package com.petrolpump.discount.config;

import com.petrolpump.discount.domain.*;
import com.petrolpump.discount.repo.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SeedData {
    @Bean
    CommandLineRunner seed(PumpRepository pumps, LoyaltyConfigRepository configs, AppUserRepository users) {
        return args -> {
            if (configs.findById(1L).isEmpty()) {
                configs.save(new LoyaltyConfig());
            }
            if (pumps.count() == 0) {
                Pump p = new Pump();
                p.setName("NAGA SHREE SERVICE STATION");
                // placeholder — update via admin when coords known
                p.setLat(13.7410);
                p.setLng(76.9060);
                p.setRadiusMeters(50);
                p.setRedeemToken("pump-demo-token");
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
