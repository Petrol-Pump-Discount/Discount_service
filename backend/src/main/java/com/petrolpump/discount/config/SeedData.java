package com.petrolpump.discount.config;

import com.petrolpump.discount.domain.*;
import com.petrolpump.discount.repo.*;
import com.petrolpump.discount.service.AuthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

@Configuration
public class SeedData {
    private static final Logger log = LoggerFactory.getLogger(SeedData.class);

    @Bean
    CommandLineRunner seed(PumpRepository pumps, LoyaltyConfigRepository configs, AppUserRepository users,
                           @Value("${app.pump.lat:13.765987}") double pumpLat,
                           @Value("${app.pump.lng:76.852652}") double pumpLng,
                           @Value("${app.pump.radius-meters:50}") double radiusMeters,
                           @Value("${app.admin-phones:}") String adminPhones,
                           @Value("${app.employee-phones:}") String employeePhones) {
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

            List<String> admins = parsePhones(adminPhones);
            List<String> employees = parsePhones(employeePhones);
            if (admins.isEmpty() && employees.isEmpty()) {
                // Local/dev fallback only when no real phones configured
                ensureRole(users, "9999999999", UserRole.ADMIN, "Admin");
                ensureRole(users, "8888888888", UserRole.EMPLOYEE, "Employee");
                log.warn("No ADMIN_PHONES / EMPLOYEE_PHONES set — seeded demo 9999999999 / 8888888888");
            } else {
                for (String phone : admins) {
                    ensureRole(users, phone, UserRole.ADMIN, "Admin");
                    log.info("Ensured ADMIN role for {}", phone);
                }
                for (String phone : employees) {
                    ensureRole(users, phone, UserRole.EMPLOYEE, "Employee");
                    log.info("Ensured EMPLOYEE role for {}", phone);
                }
            }
        };
    }

    private static List<String> parsePhones(String csv) {
        List<String> out = new ArrayList<>();
        if (csv == null || csv.isBlank()) return out;
        for (String part : csv.split("[,\\s]+")) {
            if (part.isBlank()) continue;
            try {
                out.add(AuthService.normalizePhone(part));
            } catch (Exception ex) {
                log.warn("Ignoring invalid phone in role seed: {}", part);
            }
        }
        return out;
    }

    private static void ensureRole(AppUserRepository users, String phone, UserRole role, String defaultName) {
        users.findByPhone(phone).ifPresentOrElse(u -> {
            if (u.getRole() != role) {
                u.setRole(role);
                users.save(u);
            }
        }, () -> {
            AppUser u = new AppUser();
            u.setPhone(phone);
            u.setName(defaultName);
            u.setRole(role);
            users.save(u);
        });
    }
}
