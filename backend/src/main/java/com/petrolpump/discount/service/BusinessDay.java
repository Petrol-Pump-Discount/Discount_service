package com.petrolpump.discount.service;

import java.time.*;
import java.time.format.DateTimeFormatter;

public final class BusinessDay {
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private BusinessDay() {}

    public static String of(Instant instant) {
        ZonedDateTime z = instant.atZone(IST);
        if (z.getHour() < 6) z = z.minusDays(1);
        return z.toLocalDate().format(DateTimeFormatter.ISO_LOCAL_DATE);
    }

    public static Instant startOfTrailingDays(int days) {
        String today = of(Instant.now());
        LocalDate d = LocalDate.parse(today).minusDays(days - 1L);
        return d.atTime(6, 0).atZone(IST).toInstant();
    }
}
