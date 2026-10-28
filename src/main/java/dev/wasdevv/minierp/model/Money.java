package dev.wasdevv.minierp.model;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/** Brazilian real formatting, the same as the pages: "R$ 1.234,50". */
public final class Money {

    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");

    private Money() {
    }

    public static String format(BigDecimal amount) {
        DecimalFormat format = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(PT_BR));
        return "R$ " + format.format(amount);
    }
}
