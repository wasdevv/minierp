package dev.wasdevv.minierp.service;

import dev.wasdevv.minierp.dto.ReportPeriod;
import dev.wasdevv.minierp.dto.SalesReport;
import dev.wasdevv.minierp.exception.InvalidPeriodException;
import dev.wasdevv.minierp.model.Sale;
import dev.wasdevv.minierp.repository.SaleRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SalesReportService {

    /** Longest period one report covers, which also bounds how many sales it loads. */
    static final int MAX_DAYS = 366;

    private final SaleRepository sales;
    private final Clock clock;
    private final ZoneId zone;

    public SalesReportService(SaleRepository sales, Clock clock, ZoneId shopZone) {
        this.sales = sales;
        this.clock = clock;
        this.zone = shopZone;
    }

    /** From the 1st of the current month to today, in the shop's zone. */
    public ReportPeriod currentMonth() {
        LocalDate today = LocalDate.now(clock.withZone(zone));
        return new ReportPeriod(today.withDayOfMonth(1), today);
    }

    /** Validates the two dates of the report form ({@code yyyy-MM-dd}, as {@code <input type="date">} sends). */
    public ReportPeriod parsePeriod(String from, String to) {
        if (isBlank(from) || isBlank(to)) {
            throw new InvalidPeriodException("Informe a data inicial e a data final.");
        }
        LocalDate start;
        LocalDate end;
        try {
            start = LocalDate.parse(from.strip());
            end = LocalDate.parse(to.strip());
        } catch (DateTimeParseException e) {
            throw new InvalidPeriodException("Use datas válidas no formato AAAA-MM-DD.");
        }
        if (start.isAfter(end)) {
            throw new InvalidPeriodException("A data inicial deve ser anterior ou igual à data final.");
        }
        if (ChronoUnit.DAYS.between(start, end) + 1 > MAX_DAYS) {
            throw new InvalidPeriodException("O período pode ter no máximo " + MAX_DAYS + " dias.");
        }
        return new ReportPeriod(start, end);
    }

    /** Sales from the first instant of {@code from} to the last instant of {@code to}, in the shop's zone. */
    @Transactional(readOnly = true)
    public SalesReport build(ReportPeriod period) {
        OffsetDateTime start = period.from().atStartOfDay(zone).toOffsetDateTime();
        OffsetDateTime end = period.to().plusDays(1).atStartOfDay(zone).toOffsetDateTime();
        List<SalesReport.Row> rows = sales.findForReport(start, end).stream().map(SalesReportService::row).toList();
        BigDecimal total = rows.stream().map(SalesReport.Row::total).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new SalesReport(period, rows, total);
    }

    private static SalesReport.Row row(Sale sale) {
        String customer = sale.getCustomer() == null ? "Consumidor final" : sale.getCustomer().getName();
        return new SalesReport.Row(sale.getId(), sale.getSaleDate(), customer, sale.getUnitCount(),
                sale.getTotalAmount());
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
