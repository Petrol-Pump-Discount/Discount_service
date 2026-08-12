package com.petrolpump.discount.web;

import com.petrolpump.discount.domain.RedeemTransaction;
import com.petrolpump.discount.domain.UserRole;
import com.petrolpump.discount.repo.RedeemTransactionRepository;
import com.petrolpump.discount.service.AuthService;
import com.petrolpump.discount.service.BusinessDay;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;

@RestController
@RequestMapping("/api/employee")
public class EmployeeController {
    private final AuthService auth;
    private final RedeemTransactionRepository redeems;

    public EmployeeController(AuthService auth, RedeemTransactionRepository redeems) {
        this.auth = auth; this.redeems = redeems;
    }

    private void viewer(String token) {
        auth.requireRole(token, UserRole.EMPLOYEE, UserRole.ADMIN);
    }

    @GetMapping("/redeems/live")
    public Map<String, Object> live(@RequestHeader("X-Session-Token") String token) {
        viewer(token);
        String day = BusinessDay.of(Instant.now());
        List<RedeemTransaction> list = redeems.findByBusinessDayOrderByCreatedAtDesc(day);
        long coins = list.stream().mapToLong(RedeemTransaction::getCoins).sum();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (RedeemTransaction t : list) {
            rows.add(Map.of(
                    "id", t.getId(),
                    "phone", mask(t.getUser().getPhone()),
                    "coins", t.getCoins(),
                    "rupees", t.getCoins() / 100.0,
                    "createdAt", t.getCreatedAt().toString()
            ));
        }
        return Map.of("businessDay", day, "totalCoins", coins, "totalRupees", coins / 100.0, "transactions", rows);
    }

    private static String mask(String phone) {
        if (phone == null || phone.length() < 4) return "****";
        return "******" + phone.substring(phone.length() - 4);
    }
}
