package com.petrolpump.discount.web;

import com.petrolpump.discount.domain.RedeemTransaction;
import com.petrolpump.discount.domain.UserRole;
import com.petrolpump.discount.repo.RedeemTransactionRepository;
import com.petrolpump.discount.service.AuthService;
import com.petrolpump.discount.service.BusinessDay;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.*;

@RestController
@RequestMapping("/api/employee")
public class EmployeeController {
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

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
        return summaryForRange(day, day, true);
    }

    /**
     * Redeem totals for a business-day range (6am–6am days as yyyy-MM-dd).
     * period=today|month|custom — custom needs from & to (ISO dates).
     */
    @GetMapping("/redeems/summary")
    public Map<String, Object> summary(@RequestHeader("X-Session-Token") String token,
                                       @RequestParam(defaultValue = "today") String period,
                                       @RequestParam(required = false) String from,
                                       @RequestParam(required = false) String to) {
        viewer(token);
        String today = BusinessDay.of(Instant.now());
        String start;
        String end;
        switch (period == null ? "today" : period.trim().toLowerCase(Locale.ROOT)) {
            case "month" -> {
                YearMonth ym = YearMonth.from(LocalDate.parse(today));
                start = ym.atDay(1).toString();
                end = today;
            }
            case "custom" -> {
                if (from == null || from.isBlank() || to == null || to.isBlank()) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "from and to required for custom");
                }
                start = LocalDate.parse(from.trim()).toString();
                end = LocalDate.parse(to.trim()).toString();
                if (end.compareTo(start) < 0) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "to must be on/after from");
                }
            }
            default -> {
                start = today;
                end = today;
            }
        }
        return summaryForRange(start, end, period == null || period.equalsIgnoreCase("today"));
    }

    private Map<String, Object> summaryForRange(String from, String to, boolean includeLiveList) {
        List<RedeemTransaction> list = redeems.findByBusinessDayBetweenOrderByCreatedAtDesc(from, to);
        long coins = list.stream().mapToLong(RedeemTransaction::getCoins).sum();
        List<Map<String, Object>> rows = new ArrayList<>();
        int limit = includeLiveList ? list.size() : Math.min(list.size(), 50);
        for (int i = 0; i < limit; i++) {
            RedeemTransaction t = list.get(i);
            rows.add(Map.of(
                    "id", t.getId(),
                    "phone", mask(t.getUser().getPhone()),
                    "coins", t.getCoins(),
                    "rupees", t.getCoins() / 100.0,
                    "businessDay", t.getBusinessDay(),
                    "createdAt", t.getCreatedAt().toString()
            ));
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("from", from);
        out.put("to", to);
        out.put("businessDay", from.equals(to) ? from : from + " → " + to);
        out.put("totalCoins", coins);
        out.put("totalRupees", coins / 100.0);
        out.put("txnCount", list.size());
        out.put("transactions", rows);
        out.put("timezone", IST.getId());
        return out;
    }

    private static String mask(String phone) {
        if (phone == null || phone.length() < 4) return "****";
        return "******" + phone.substring(phone.length() - 4);
    }
}
