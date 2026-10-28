package dev.wasdevv.minierp.model;

/** Brazilian phone numbers as digits with area code: 10 (landline) or 11 (mobile, starting with 9). */
public final class Phone {

    private Phone() {
    }

    /** Digits only, without a leading +55 or 0 (trunk prefix); {@code null} when nothing is left. */
    public static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String digits = value.replaceAll("\\D", "");
        if (digits.length() > 11 && digits.startsWith("55")) {
            digits = digits.substring(2);
        }
        if (digits.length() > 11 && digits.startsWith("0")) {
            digits = digits.substring(1);
        }
        return digits.isEmpty() ? null : digits;
    }

    public static boolean isValid(String digits) {
        if (digits == null || !digits.matches("[0-9]{10,11}") || digits.charAt(0) == '0') {
            return false;
        }
        return digits.length() == 10 || digits.charAt(2) == '9';
    }

    public static String format(String digits) {
        if (digits == null) {
            return null;
        }
        return switch (digits.length()) {
            case 10 -> "(" + digits.substring(0, 2) + ") " + digits.substring(2, 6) + "-" + digits.substring(6);
            case 11 -> "(" + digits.substring(0, 2) + ") " + digits.substring(2, 7) + "-" + digits.substring(7);
            default -> digits;
        };
    }
}
