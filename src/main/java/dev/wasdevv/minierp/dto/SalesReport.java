package dev.wasdevv.minierp.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/** Sales of a period, already reduced to what the report shows: no customer documents or contacts. */
public record SalesReport(ReportPeriod period, List<Row> rows, BigDecimal totalAmount) {

    public record Row(Long saleId, OffsetDateTime saleDate, String customerName, int units, BigDecimal total) {
    }

    public int saleCount() {
        return rows.size();
    }

    public boolean isEmpty() {
        return rows.isEmpty();
    }
}
