package dev.wasdevv.minierp.service;

import dev.wasdevv.minierp.dto.SaleForm;
import dev.wasdevv.minierp.dto.SaleItemForm;
import dev.wasdevv.minierp.exception.ResourceNotFoundException;
import dev.wasdevv.minierp.exception.SaleRejectedException;
import dev.wasdevv.minierp.model.Customer;
import dev.wasdevv.minierp.model.Product;
import dev.wasdevv.minierp.model.Sale;
import dev.wasdevv.minierp.model.SaleItem;
import dev.wasdevv.minierp.repository.CustomerRepository;
import dev.wasdevv.minierp.repository.ProductRepository;
import dev.wasdevv.minierp.repository.SaleRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
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

    private final SaleRepository sales;
    private final ProductRepository products;
    private final CustomerRepository customers;
    private final Clock clock;

    public SaleService(SaleRepository sales, ProductRepository products, CustomerRepository customers, Clock clock) {
        this.sales = sales;
        this.products = products;
        this.customers = customers;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Page<Sale> list(Pageable pageable) {
        return sales.findAllBy(pageable);
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
        if (!problems.isEmpty()) {
            throw new SaleRejectedException(problems);
        }

        // Same transaction as the sale insert: if anything below fails, the stock goes back too.
        sale.getItems().forEach(item -> item.getProduct().removeStock(item.getQuantity()));
        sale.setCustomer(customer);
        sale.setSaleDate(OffsetDateTime.now(clock));
        sale.setTotalAmount(total);
        return sales.save(sale);
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
