package dev.wasdevv.minierp.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.wasdevv.minierp.dto.ReportPeriod;
import dev.wasdevv.minierp.exception.InvalidPeriodException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class SalesReportServiceTest {

    private static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");

    // 2026-11-01 01:00 UTC is still 31/10 22:00 in São Paulo.
    private final SalesReportService service = new SalesReportService(null,
            Clock.fixed(Instant.parse("2026-11-01T01:00:00Z"), ZoneOffset.UTC), SAO_PAULO);

    @Test
    void currentMonthUsesTheShopsDateNotUtc() {
        assertThat(service.currentMonth()).isEqualTo(new ReportPeriod(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31)));
    }

    @Test
    void parsesIsoDates() {
        assertThat(service.parsePeriod(" 2026-10-01 ", "2026-10-27"))
                .isEqualTo(new ReportPeriod(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 27)));
    }

    @Test
    void acceptsSingleDayPeriod() {
        assertThat(service.parsePeriod("2026-10-05", "2026-10-05").from()).isEqualTo(LocalDate.of(2026, 10, 5));
    }

    @Test
    void rejectsMissingDates() {
        assertThatThrownBy(() -> service.parsePeriod("", "2026-10-27"))
                .isInstanceOf(InvalidPeriodException.class).hasMessage("Informe a data inicial e a data final.");
        assertThatThrownBy(() -> service.parsePeriod("2026-10-01", null)).isInstanceOf(InvalidPeriodException.class);
    }

    @Test
    void rejectsMalformedDates() {
        assertThatThrownBy(() -> service.parsePeriod("01/10/2026", "2026-10-27"))
                .hasMessage("Use datas válidas no formato AAAA-MM-DD.");
        assertThatThrownBy(() -> service.parsePeriod("2026-02-30", "2026-03-01"))
                .hasMessage("Use datas válidas no formato AAAA-MM-DD.");
    }

    @Test
    void rejectsStartAfterEnd() {
        assertThatThrownBy(() -> service.parsePeriod("2026-10-28", "2026-10-27"))
                .hasMessage("A data inicial deve ser anterior ou igual à data final.");
    }

    @Test
    void limitsThePeriodLength() {
        assertThat(service.parsePeriod("2024-01-01", "2024-12-31").to()).isEqualTo(LocalDate.of(2024, 12, 31));
        assertThatThrownBy(() -> service.parsePeriod("2025-01-01", "2026-01-02"))
                .hasMessage("O período pode ter no máximo 366 dias.");
    }

    @Test
    void labelsThePeriodInBrazilianFormat() {
        assertThat(new ReportPeriod(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 27)).label())
                .isEqualTo("01/10/2026 a 27/10/2026");
    }
}
