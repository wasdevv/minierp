package dev.wasdevv.minierp.dto;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/** A closed range of calendar days in the shop's zone. */
public record ReportPeriod(LocalDate from, LocalDate to) {

    private static final DateTimeFormatter BR = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public String label() {
        return from.format(BR) + " a " + to.format(BR);
    }
}
