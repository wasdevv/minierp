package dev.wasdevv.minierp.service;

import dev.wasdevv.minierp.dto.SaleFilter;
import dev.wasdevv.minierp.dto.SaleForm;
import dev.wasdevv.minierp.dto.SaleItemForm;
import dev.wasdevv.minierp.exception.ResourceNotFoundException;
import dev.wasdevv.minierp.exception.SaleRejectedException;
import dev.wasdevv.minierp.model.Customer;
import dev.wasdevv.minierp.model.Money;
import dev.wasdevv.minierp.model.PaymentMethod;
import dev.wasdevv.minierp.model.Product;
import dev.wasdevv.minierp.model.Sale;
import dev.wasdevv.minierp.model.SaleItem;
import dev.wasdevv.minierp.repository.CustomerRepository;
import dev.wasdevv.minierp.repository.ProductRepository;
import dev.wasdevv.minierp.repository.SaleRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SaleService {

    /** Largest value a NUMERIC(12, 2) column holds. */
    static final BigDecimal MAX_AMOUNT = new BigDecimal("9999999999.99");
    static final int MAX_LINES = 50;
    static final int MAX_QUANTITY = 100_000;
    static final int MAX_REASON = 200;

    private final SaleRepository sales;
    private final ProductRepository products;
    private final CustomerRepository customers;
    private static final OffsetDateTime EARLIEST = OffsetDateTime.parse("1970-01-01T00:00:00Z");
    private static final OffsetDateTime LATEST = OffsetDateTime.parse("9999-01-01T00:00:00Z");

    private final StockService stock;
    private final Clock clock;
    private final ZoneId zone;

    public SaleService(SaleRepository sales, ProductRepository products, CustomerRepository customers,
                       StockService stock, Clock clock, ZoneId shopZone) {
        this.sales = sales;
        this.products = products;
        this.customers = customers;
        this.stock = stock;
        this.clock = clock;
        this.zone = shopZone;
    }

    /** The filtered list and, for the same filters, how many confirmed sales and how much they add up to. */
    @Transactional(readOnly = true)
    public SaleSearch search(SaleFilter filter, Pageable pageable) {
        boolean anyCustomer = filter.query().isEmpty();
        String term = SearchTerms.escapeLike(filter.query());
        // -1 matches no sale: a query that is not a number only searches customer names.
        Long saleId = filter.saleId() == null ? -1L : filter.saleId();
        // Open ends become far-away instants rather than nulls, which PostgreSQL cannot type in "x >= ?".
        OffsetDateTime start = filter.from() == null ? EARLIEST : filter.from().atStartOfDay(zone).toOffsetDateTime();
        OffsetDateTime end = filter.to() == null ? LATEST : filter.to().plusDays(1).atStartOfDay(zone).toOffsetDateTime();
        Object[] totals = sales.searchTotals(anyCustomer, term, saleId, start, end, filter.status(), filter.paymentMethod()).getFirst();
        return new SaleSearch(sales.search(anyCustomer, term, saleId, start, end, filter.status(), filter.paymentMethod(), pageable),
                ((Number) totals[0]).longValue(), (BigDecimal) totals[1]);
    }

    public record SaleSearch(Page<Sale> page, long confirmedSales, BigDecimal confirmedTotal) {
    }

    @Transactional(readOnly = true)
    public Sale get(Long id) {
        return sales.findWithItemsById(id).orElseThrow(() -> new ResourceNotFoundException("Sale", id));
    }

    /**
     * Records a sale and takes its items out of stock, atomically. Every price and total comes from the
     * database at this moment; the form carries only product ids and quantities. Lines repeating a product
     * are merged, so a product's stock is checked against everything the sale asks for.
     */
    @Transactional
    public Sale create(SaleForm form) {
        Map<Long, Integer> quantities = validatedQuantities(form);
        // Locked until commit: a concurrent sale of the same product waits here and then sees the new stock.
        Map<Long, Product> byId = products.findAllForUpdate(quantities.keySet()).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));

        List<String> problems = new ArrayList<>();
        Sale sale = new Sale();
        BigDecimal total = BigDecimal.ZERO;
        for (Map.Entry<Long, Integer> line : quantities.entrySet()) {
            Product product = byId.get(line.getKey());
            if (product == null) {
                problems.add("Um dos produtos escolhidos não existe mais.");
                continue;
            }
            if (!product.isActive()) {
                problems.add("O produto " + product.getName() + " está inativo e não pode ser vendido.");
                continue;
            }
            if (product.getStockQuantity() < line.getValue()) {
                problems.add("Estoque insuficiente para " + product.getName() + ": disponível "
                        + product.getStockQuantity() + ", solicitado " + line.getValue() + ".");
                continue;
            }
            SaleItem item = new SaleItem(product, line.getValue());
            sale.addItem(item);
            total = total.add(item.getSubtotal());
        }
        if (problems.isEmpty() && total.compareTo(MAX_AMOUNT) > 0) {
            problems.add("O total da venda passa do limite de R$ 9.999.999.999,99.");
        }
        Customer customer = form.getCustomerId() == null ? null : customers.findById(form.getCustomerId())
                .orElse(null);
        if (form.getCustomerId() != null && customer == null) {
            problems.add("O cliente escolhido não existe mais.");
        }
        BigDecimal discount = discount(form, total, problems);
        BigDecimal toPay = total.subtract(discount);
        BigDecimal received = payment(form, customer, toPay, problems);
        if (!problems.isEmpty()) {
            throw new SaleRejectedException(problems);
        }

        // Same transaction as the sale insert: if anything below fails, the stock goes back too.
        sale.getItems().forEach(item -> item.getProduct().removeStock(item.getQuantity()));
        sale.setCustomer(customer);
        sale.setSaleDate(OffsetDateTime.now(clock));
        sale.price(total, discount);
        sale.pay(form.getPaymentMethod(), received);
        Sale saved = sales.save(sale);
        stock.recordSale(saved);
        return saved;
    }

    /**
     * Cancels a confirmed sale and puts its items back in stock, atomically (README rule 7). The sale row is
     * locked first, so a second cancellation waits and then finds the sale already cancelled; the products
     * are locked in id order, like {@link #create}.
     */
    @Transactional
    public Sale cancel(Long id, String reason) {
        String why = reason == null ? "" : reason.strip();
        if (why.isEmpty()) {
            throw new SaleRejectedException("Informe o motivo do cancelamento.");
        }
        if (why.length() > MAX_REASON) {
            throw new SaleRejectedException("O motivo pode ter no máximo " + MAX_REASON + " caracteres.");
        }
        Sale sale = sales.findForUpdate(id).orElseThrow(() -> new ResourceNotFoundException("Sale", id));
        if (sale.isCancelled()) {
            throw new SaleRejectedException("Esta venda já foi cancelada.");
        }
        Map<Long, Product> byId = products.findAllForUpdate(
                        sale.getItems().stream().map(item -> item.getProduct().getId()).toList()).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
        sale.getItems().forEach(item -> byId.get(item.getProduct().getId()).addStock(item.getQuantity()));
        sale.cancel(OffsetDateTime.now(clock), why);
        stock.recordCancellation(sale, List.copyOf(byId.values()));
        return sale;
    }

    /** What the form shows as a running total. Indicative only: {@link #create} recomputes everything. */
    @Transactional(readOnly = true)
    public BigDecimal estimateTotal(SaleForm form) {
        List<Long> ids = form.filledItems().stream().map(SaleItemForm::getProductId)
                .filter(id -> id != null).toList();
        Map<Long, Product> byId = products.findAllById(ids).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
        BigDecimal total = BigDecimal.ZERO;
        for (SaleItemForm item : form.filledItems()) {
            Product product = item.getProductId() == null ? null : byId.get(item.getProductId());
            if (product != null && item.getQuantity() != null && item.getQuantity() > 0) {
                total = total.add(product.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())));
            }
        }
        return total;
    }

    /** The discount in reais: a value or a percentage of the subtotal, never more than the subtotal. */
    public static BigDecimal discount(SaleForm form, BigDecimal subtotal, List<String> problems) {
        BigDecimal value = form.getDiscountValue();
        if (value == null || value.signum() == 0) {
            return BigDecimal.ZERO;
        }
        if (value.signum() < 0) {
            problems.add("O desconto não pode ser negativo.");
            return BigDecimal.ZERO;
        }
        if (form.isPercentDiscount()) {
            if (value.compareTo(BigDecimal.valueOf(100)) > 0) {
                problems.add("O desconto pode ser de no máximo 100%.");
                return BigDecimal.ZERO;
            }
            return subtotal.multiply(value).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        }
        if (value.scale() > 2 && value.stripTrailingZeros().scale() > 2) {
            problems.add("Use no máximo 2 casas decimais no desconto.");
            return BigDecimal.ZERO;
        }
        if (value.compareTo(subtotal) > 0) {
            problems.add("O desconto não pode passar do subtotal da venda.");
            return BigDecimal.ZERO;
        }
        return value.setScale(2, RoundingMode.UNNECESSARY);
    }

    /** Validates the payment method and returns the cash received (or {@code null}). */
    private static BigDecimal payment(SaleForm form, Customer customer, BigDecimal toPay, List<String> problems) {
        PaymentMethod method = form.getPaymentMethod();
        if (method == null) {
            problems.add("Escolha a forma de pagamento.");
            return null;
        }
        if (method == PaymentMethod.ON_ACCOUNT && customer == null && form.getCustomerId() == null) {
            problems.add("Venda fiado precisa de um cliente, para saber quem vai pagar.");
        }
        BigDecimal received = form.getAmountReceived();
        if (method != PaymentMethod.CASH || received == null) {
            return null;
        }
        if (received.stripTrailingZeros().scale() > 2) {
            problems.add("Use no máximo 2 casas decimais no valor recebido.");
            return null;
        }
        if (received.compareTo(toPay) < 0) {
            problems.add("O valor recebido (" + Money.format(received) + ") é menor que o total (" + Money.format(toPay) + ").");
            return null;
        }
        if (received.compareTo(MAX_AMOUNT) > 0) {
            problems.add("O valor recebido é grande demais.");
            return null;
        }
        return received.setScale(2, RoundingMode.UNNECESSARY);
    }

    /** Product id to quantity, in form order, after the per-line checks. */
    private static Map<Long, Integer> validatedQuantities(SaleForm form) {
        List<SaleItemForm> filled = form.filledItems();
        List<String> problems = new ArrayList<>();
        if (filled.isEmpty()) {
            problems.add("Adicione ao menos um item à venda.");
        }
        if (filled.size() > MAX_LINES) {
            problems.add("Uma venda pode ter no máximo " + MAX_LINES + " itens.");
        }
        Map<Long, Integer> quantities = new LinkedHashMap<>();
        int line = 0;
        for (SaleItemForm item : form.getItems()) {
            line++;
            if (item.isBlank()) {
                continue;
            }
            if (item.getProductId() == null) {
                problems.add("Item " + line + ": escolha o produto.");
            }
            if (item.getQuantity() == null) {
                problems.add("Item " + line + ": informe a quantidade.");
            } else if (item.getQuantity() <= 0) {
                problems.add("Item " + line + ": a quantidade deve ser maior que zero.");
            } else if (item.getQuantity() > MAX_QUANTITY) {
                problems.add("Item " + line + ": a quantidade máxima por item é " + MAX_QUANTITY + ".");
            }
            if (item.getProductId() != null && item.getQuantity() != null && item.getQuantity() > 0
                    && item.getQuantity() <= MAX_QUANTITY) {
                quantities.merge(item.getProductId(), item.getQuantity(), Integer::sum);
            }
        }
        quantities.forEach((productId, quantity) -> {
            if (quantity > MAX_QUANTITY) {
                problems.add("A quantidade somada de um mesmo produto passa de " + MAX_QUANTITY + ".");
            }
        });
        if (!problems.isEmpty()) {
            throw new SaleRejectedException(problems);
        }
        return quantities;
    }
}
