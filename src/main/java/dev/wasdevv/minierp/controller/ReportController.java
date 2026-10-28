package dev.wasdevv.minierp.controller;

import dev.wasdevv.minierp.dto.ReportPeriod;
import dev.wasdevv.minierp.exception.InvalidPeriodException;
import dev.wasdevv.minierp.service.SalesReportPdf;
import dev.wasdevv.minierp.service.SalesReportService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.util.UriComponentsBuilder;

@Controller
@RequestMapping("/reports")
public class ReportController {

    private final SalesReportService reports;
    private final SalesReportPdf pdf;

    public ReportController(SalesReportService reports, SalesReportPdf pdf) {
        this.reports = reports;
        this.pdf = pdf;
    }

    /** The period form and an on-screen preview of the same data the PDF carries. */
    @GetMapping("/sales")
    public String sales(@RequestParam(name = "from", required = false) String from,
                        @RequestParam(name = "to", required = false) String to,
                        Model model) {
        model.addAttribute("from", from == null ? "" : from);
        model.addAttribute("to", to == null ? "" : to);
        if (from == null && to == null) {
            ReportPeriod month = reports.currentMonth();
            model.addAttribute("from", month.from().toString());
            model.addAttribute("to", month.to().toString());
            model.addAttribute("report", reports.build(month));
            return "reports/sales";
        }
        try {
            model.addAttribute("report", reports.build(reports.parsePeriod(from, to)));
        } catch (InvalidPeriodException e) {
            model.addAttribute("problem", e.getMessage());
        }
        return "reports/sales";
    }

    @GetMapping("/sales.pdf")
    public ResponseEntity<byte[]> salesPdf(@RequestParam(name = "from", required = false) String from,
                                           @RequestParam(name = "to", required = false) String to) {
        ReportPeriod period;
        try {
            period = reports.parsePeriod(from, to);
        } catch (InvalidPeriodException e) {
            // Back to the form, which shows the same message next to the fields.
            String back = UriComponentsBuilder.fromPath("/reports/sales")
                    .queryParam("from", from == null ? "" : from)
                    .queryParam("to", to == null ? "" : to)
                    .encode().toUriString();
            return ResponseEntity.status(303).header(HttpHeaders.LOCATION, back).build();
        }
        byte[] body = pdf.render(reports.build(period));
        String filename = "vendas-" + period.from() + "-a-" + period.to() + ".pdf";
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename).build().toString())
                .body(body);
    }
}
