package dev.wasdevv.minierp.dto;

import dev.wasdevv.minierp.model.Product;
import dev.wasdevv.minierp.model.Sale;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

/** What the home page shows. Revenue counts confirmed sales only. */
public record Dashboard(
        LocalDate date,
        Period today,
        Period month,
        List<Day> lastDays,
        List<TopProduct> topProducts,
        List<Product> lowStock,
        List<Sale> recentSales,
        long activeProducts,
        long customers,
        Profit monthProfit) {

    /**
     * Gross profit of the month over the sales whose items all have a cost: what was paid (after discount)
     * minus the cost of the items. Sales with an item without cost are left out and counted, so a partial
     * figure is never shown as the whole, and {@code amount} is {@code null} when no sale could be counted.
     */
    public record Profit(BigDecimal amount, long salesWithoutCost) {

        public boolean isPartial() {
            return amount != null && salesWithoutCost > 0;
        }

        public boolean isUnknown() {
            return amount == null;
        }
    }

    public record Period(long sales, BigDecimal revenue) {

        public BigDecimal averageTicket() {
            return sales == 0 ? BigDecimal.ZERO : revenue.divide(BigDecimal.valueOf(sales), 2, RoundingMode.HALF_EVEN);
        }
    }

    public record Day(LocalDate date, long sales, BigDecimal revenue, int heightPercent) {
    }

    public record TopProduct(String name, String sku, long units, BigDecimal revenue) {
    }

    public BigDecimal lastDaysRevenue() {
        return lastDays.stream().map(Day::revenue).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public boolean isEmpty() {
        return activeProducts == 0 && customers == 0 && recentSales.isEmpty();
    }
}
