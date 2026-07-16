package com.erp.util;

import java.util.Locale;
import java.util.Set;

public final class PaymentMethodLabels {

    public static final String PENDING_VENDOR = "PENDING_VENDOR_PAYMENT";
    public static final String PENDING_REQUEST = "PENDING_REQUEST";
    public static final String PENDING_OTHER = "PENDING_OTHER_PAYMENT";

    private static final Set<String> ALLOWED_METHODS = Set.of(
            "CASH", "CARD", "BANK_TRANSFER", "CHEQUE", "UPI", "OTHER");

    private PaymentMethodLabels() {
    }

    public static String normalizeMethod(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Payment method is required");
        }
        String code = raw.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
        if (!ALLOWED_METHODS.contains(code)) {
            throw new IllegalArgumentException(
                    "Invalid payment method. Use one of: cash, card, bank transfer, cheque, UPI, other.");
        }
        return code;
    }

    public static String displayLabel(String code) {
        if (code == null || code.isBlank()) {
            return "—";
        }
        return switch (code.trim().toUpperCase(Locale.ROOT)) {
            case "CASH" -> "Cash";
            case "CARD" -> "Card";
            case "BANK_TRANSFER" -> "Bank transfer";
            case "CHEQUE" -> "Cheque";
            case "UPI" -> "UPI";
            case "OTHER" -> "Other";
            case PENDING_VENDOR -> "Pending vendor payment";
            case PENDING_REQUEST -> "Pending request";
            case PENDING_OTHER -> "Pending expense payment";
            case "CANCELLED" -> "Cancelled";
            default -> code.replace('_', ' ');
        };
    }
}
