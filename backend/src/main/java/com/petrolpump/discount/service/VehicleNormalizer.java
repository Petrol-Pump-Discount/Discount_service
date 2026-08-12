package com.petrolpump.discount.service;

public final class VehicleNormalizer {
    private VehicleNormalizer() {}
    public static String normalize(String raw) {
        if (raw == null) return "";
        return raw.replaceAll("[^A-Za-z0-9]", "").toUpperCase();
    }
    public static boolean isBlankOnBill(String raw) {
        String n = normalize(raw);
        return n.isEmpty() || n.equals("NOTENTERED") || n.equals("NOTENTRED");
    }
}
