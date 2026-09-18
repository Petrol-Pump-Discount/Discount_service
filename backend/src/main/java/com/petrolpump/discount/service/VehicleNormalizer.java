package com.petrolpump.discount.service;

import java.util.regex.Pattern;

/**
 * Indian registration plates (spaces/hyphens stripped).
 * Covers: current RTO, older 1-digit RTO, pre-series, BH, CD/CC/UN diplomatic.
 */
public final class VehicleNormalizer {
    private VehicleNormalizer() {}

    private static final Pattern STANDARD =
            Pattern.compile("^[A-Z]{2}[0-9]{1,2}[A-Z]{0,3}[0-9]{4}$");
    private static final Pattern BH =
            Pattern.compile("^[0-9]{2}BH[0-9]{4}[A-Z]{1,2}$");
    private static final Pattern DIPLOMATIC =
            Pattern.compile("^[0-9]{1,3}(CD|CC|UN)[0-9]{1,4}[A-Z]{0,2}$");

    public static String normalize(String raw) {
        if (raw == null) return "";
        return raw.replaceAll("[^A-Za-z0-9]", "").toUpperCase();
    }

    public static boolean isBlankOnBill(String raw) {
        String n = normalize(raw);
        return n.isEmpty() || n.equals("NOTENTERED") || n.equals("NOTENTRED");
    }

    public static boolean isValid(String raw) {
        String v = normalize(raw);
        if (v.isEmpty() || v.equals("NOTENTERED") || v.equals("NOTENTRED")) return false;
        if (v.length() < 6 || v.length() > 12) return false;
        return STANDARD.matcher(v).matches()
                || BH.matcher(v).matches()
                || DIPLOMATIC.matcher(v).matches();
    }
}
