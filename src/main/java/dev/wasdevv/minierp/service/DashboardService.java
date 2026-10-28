package dev.wasdevv.minierp.service;

import dev.wasdevv.minierp.dto.Dashboard;
import dev.wasdevv.minierp.model.Product;
import dev.wasdevv.minierp.model.Sale;
import dev.wasdevv.minierp.repository.CustomerRepository;
import dev.wasdevv.minierp.repository.ProductRepository;
import dev.wasdevv.minierp.repository.SaleItemRepository;
import dev.wasdevv.minierp.repository.SaleRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DashboardService {

    static final int CHART_DAYS = 14;
    static final int LIST_SIZE = 5;

    private final SaleRepository sales;
    private final SaleItemRepository saleItems;
    private final ProductRepository products;
    private final CustomerRepository customers;
    private final Clock clock;
    private final ZoneId zone;

    public DashboardService(SaleRepository sales, SaleItemRepository saleItems, ProductRepository products,
                            CustomerRepository customers, Clock clock, ZoneId shopZone) {
        this.sales = sales;
        this.saleItems = saleItems;
        this.products = products;
        this.customers = customers;
        this.clock = clock;
        this.zone = shopZone;
    }

    @Transactional(readOnly = true)
    public Dashboard build() {
        LocalDate today = LocalDate.now(clock.withZone(zone));
        LocalDate firstOfMonth = today.withDayOfMonth(1);
        LocalDate chartStart = today.minusDays(CHART_DAYS - 1);
        LocalDate earliest = chartStart.isBefore(firstOfMonth) ? chartStart : firstOfMonth;

        // One query for everything time-based: confirmed sales since the earliest day any card needs.
        List<Sale> recent = sales.findForReport(startOf(earliest), startOf(today.plusDays(1)));
        Map<LocalDate, List<Sale>> byDay = recent.stream()
                .collect(Collectors.groupingBy(sale -> sale.getSaleDate().atZoneSameInstant(zone).toLocalDate()));

        Dashboard.Period todayPeriod = period(byDay.getOrDefault(today, List.of()));
        Dashboard.Period monthPeriod = period(recent.stream()
                .filter(sale -> !sale.getSaleDate().atZoneSameInstant(zone).toLocalDate().isBefore(firstOfMonth))
                .toList());

        List<Dashboard.Period> days = new ArrayList<>();
        for (int i = 0; i < CHART_DAYS; i++) {
            days.add(period(byDay.getOrDefault(chartStart.plusDays(i), List.of())));
        }
        BigDecimal max = days.stream().map(Dashboard.Period::revenue).reduce(BigDecimal.ZERO, BigDecimal::max);
        List<Dashboard.Day> chart = new ArrayList<>();
        for (int i = 0; i < CHART_DAYS; i++) {
            Dashboard.Period day = days.get(i);
            int height = max.signum() == 0 ? 0
                    : day.revenue().multiply(BigDecimal.valueOf(100)).divide(max, 0, RoundingMode.UP).intValue();
            chart.add(new Dashboard.Day(chartStart.plusDays(i), day.sales(), day.revenue(), height));
        }

        return new Dashboard(
                today,
                todayPeriod,
                monthPeriod,
                chart,
                saleItems.topProducts(startOf(firstOfMonth), startOf(today.plusDays(1)), PageRequest.of(0, LIST_SIZE)),
                products.findLowStock(Product.LOW_STOCK, PageRequest.of(0, LIST_SIZE + 1)),
                sales.findAllBy(PageRequest.of(0, LIST_SIZE,
                        Sort.by(Sort.Order.desc("saleDate"), Sort.Order.desc("id")))).getContent(),
                products.countByActiveTrue(),
                customers.count());
    }

    private OffsetDateTime startOf(LocalDate day) {
        return day.atStartOfDay(zone).toOffsetDateTime();
    }

    private static Dashboard.Period period(List<Sale> sales) {
        BigDecimal revenue = sales.stream().map(Sale::getTotalAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new Dashboard.Period(sales.size(), revenue);
    }
}
