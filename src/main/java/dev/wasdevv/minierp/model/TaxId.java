package dev.wasdevv.minierp.model;

/**
 * Brazilian taxpayer ids: CPF (11 digits, people) and CNPJ (14 digits, companies). Values are handled as
 * digits only; punctuation exists only for display.
 */
public final class TaxId {

    private TaxId() {
    }

    /** Digits only, or {@code null} when nothing is left. */
    public static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String digits = value.replaceAll("\\D", "");
        return digits.isEmpty() ? null : digits;
    }

    public static boolean isValid(String digits) {
        if (digits == null || !digits.chars().allMatch(c -> c >= '0' && c <= '9')) {
            return false;
        }
        return switch (digits.length()) {
            case 11 -> isValidCpf(digits);
            case 14 -> isValidCnpj(digits);
            default -> false;
        };
    }

    public static String format(String digits) {
        if (digits == null) {
            return null;
        }
        return switch (digits.length()) {
            case 11 -> digits.replaceFirst("(\\d{3})(\\d{3})(\\d{3})(\\d{2})", "$1.$2.$3-$4");
            case 14 -> digits.replaceFirst("(\\d{2})(\\d{3})(\\d{3})(\\d{4})(\\d{2})", "$1.$2.$3/$4-$5");
            default -> digits;
        };
    }

    /** Hides the first block and the check digits, the usual way a CPF/CNPJ is shown on screen. */
    public static String mask(String digits) {
        if (digits == null) {
            return null;
        }
        return switch (digits.length()) {
            case 11 -> "***." + digits.substring(3, 6) + "." + digits.substring(6, 9) + "-**";
            case 14 -> "**." + digits.substring(2, 5) + "." + digits.substring(5, 8) + "/"
                    + digits.substring(8, 12) + "-**";
            default -> "***";
        };
    }

    private static boolean isValidCpf(String digits) {
        if (allSame(digits)) {
            return false;
        }
        return checkDigit(digits, 9, 10) == digit(digits, 9) && checkDigit(digits, 10, 11) == digit(digits, 10);
    }

    /** Weights start at {@code firstWeight} and go down to 2 over the first {@code length} digits. */
    private static int checkDigit(String digits, int length, int firstWeight) {
        int sum = 0;
        for (int i = 0; i < length; i++) {
            sum += digit(digits, i) * (firstWeight - i);
        }
        int rest = sum % 11;
        return rest < 2 ? 0 : 11 - rest;
    }

    private static boolean isValidCnpj(String digits) {
        if (allSame(digits)) {
            return false;
        }
        return cnpjCheckDigit(digits, 12) == digit(digits, 12) && cnpjCheckDigit(digits, 13) == digit(digits, 13);
    }

    /** CNPJ weights cycle 2..9 from the rightmost digit leftwards. */
    private static int cnpjCheckDigit(String digits, int length) {
        int sum = 0;
        int weight = 2;
        for (int i = length - 1; i >= 0; i--) {
            sum += digit(digits, i) * weight;
            weight = weight == 9 ? 2 : weight + 1;
        }
        int rest = sum % 11;
        return rest < 2 ? 0 : 11 - rest;
    }

    private static boolean allSame(String digits) {
        return digits.chars().distinct().count() == 1;
    }

    private static int digit(String digits, int index) {
        return digits.charAt(index) - '0';
    }
}
