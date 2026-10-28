package dev.wasdevv.minierp.service;

import dev.wasdevv.minierp.dto.SalesReport;
import dev.wasdevv.minierp.model.Money;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JRParameter;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperExportManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.data.JRMapCollectionDataSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * Fills {@code reports/sales.jrxml} and exports it as PDF. Values reach the template already formatted,
 * so the template holds layout only and its numbers look exactly like the pages'.
 */
@Component
public class SalesReportPdf {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");

    private final JasperReport template;
    private final Clock clock;
    private final ZoneId zone;

    public SalesReportPdf(Clock clock, ZoneId shopZone) {
        this.clock = clock;
        this.zone = shopZone;
        // Compiled once at startup: a broken template fails the boot instead of the first download.
        try (InputStream jrxml = new ClassPathResource("reports/sales.jrxml").getInputStream()) {
            this.template = JasperCompileManager.compileReport(jrxml);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (JRException e) {
            throw new IllegalStateException("Could not compile reports/sales.jrxml", e);
        }
    }

    public byte[] render(SalesReport report) {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("PERIOD", report.period().label());
        parameters.put("GENERATED_AT", ZonedDateTime.now(clock.withZone(zone)).format(DATE_TIME));
        parameters.put("SALE_COUNT", String.valueOf(report.saleCount()));
        parameters.put("TOTAL_AMOUNT", Money.format(report.totalAmount()));
        parameters.put("EMPTY", report.isEmpty());
        parameters.put(JRParameter.REPORT_LOCALE, PT_BR);

        List<Map<String, ?>> rows = report.rows().stream().<Map<String, ?>>map(row -> Map.of(
                "date", row.saleDate().atZoneSameInstant(zone).format(DATE_TIME),
                "sale", "#" + row.saleId(),
                "customer", row.customerName(),
                "units", String.valueOf(row.units()),
                "total", Money.format(row.total()))).toList();

        try {
            JasperPrint print = JasperFillManager.fillReport(template, parameters, new JRMapCollectionDataSource(rows));
            return JasperExportManager.exportReportToPdf(print);
        } catch (JRException e) {
            throw new IllegalStateException("Could not render the sales report", e);
        }
    }
}
