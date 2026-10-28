package dev.wasdevv.minierp.dto;

import dev.wasdevv.minierp.model.Customer;
import dev.wasdevv.minierp.model.Sale;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import org.springframework.data.domain.Page;

/** A customer's page: who they are, what they bought, and the totals of their confirmed sales. */
public record CustomerHistory(Customer customer, Page<Sale> sales, long confirmedSales, BigDecimal totalSpent,
                              BigDecimal onAccount, OffsetDateTime lastPurchase) {

    public BigDecimal averageTicket() {
        return confirmedSales == 0 ? BigDecimal.ZERO
                : totalSpent.divide(BigDecimal.valueOf(confirmedSales), 2, RoundingMode.HALF_EVEN);
    }
}
