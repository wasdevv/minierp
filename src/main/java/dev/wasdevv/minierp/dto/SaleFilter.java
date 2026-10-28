package dev.wasdevv.minierp.dto;

import dev.wasdevv.minierp.model.PaymentMethod;
import dev.wasdevv.minierp.model.SaleStatus;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/**
 * The filters of the sales list, parsed leniently from the query string: a value that does not parse is
 * dropped (and reported in {@link #problem}), never a 400.
 */
public record SaleFilter(String query, Long saleId, LocalDate from, LocalDate to, SaleStatus status,
                         PaymentMethod paymentMethod, String problem) {

    public static SaleFilter parse(String q, String from, String to, String status, String payment) {
        String query = q == null ? "" : q.strip();
        Long saleId = null;
        String digits = query.startsWith("#") ? query.substring(1) : query;
        if (!digits.isEmpty() && digits.length() <= 18 && digits.chars().allMatch(Character::isDigit)) {
            saleId = Long.valueOf(digits);
        }
        String problem = null;
        LocalDate start = null;
        LocalDate end = null;
        try {
            start = isBlank(from) ? null : LocalDate.parse(from.strip());
            end = isBlank(to) ? null : LocalDate.parse(to.strip());
        } catch (DateTimeParseException e) {
            problem = "Use datas válidas no formato AAAA-MM-DD.";
            start = null;
            end = null;
        }
        if (start != null && end != null && start.isAfter(end)) {
            problem = "A data inicial deve ser anterior ou igual à data final.";
            start = null;
            end = null;
        }
        return new SaleFilter(query, saleId, start, end, parse(SaleStatus.class, status),
                parse(PaymentMethod.class, payment), problem);
    }

    public boolean isActive() {
        return !query.isEmpty() || from != null || to != null || status != null || paymentMethod != null;
    }

    private static <E extends Enum<E>> E parse(Class<E> type, String value) {
        if (isBlank(value)) {
            return null;
        }
        try {
            return Enum.valueOf(type, value.strip());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
