package dev.wasdevv.minierp.service;

import dev.wasdevv.minierp.config.CurrentUser;
import dev.wasdevv.minierp.exception.ResourceNotFoundException;
import dev.wasdevv.minierp.exception.StockRejectedException;
import dev.wasdevv.minierp.model.Product;
import dev.wasdevv.minierp.model.Sale;
import dev.wasdevv.minierp.model.StockMovement;
import dev.wasdevv.minierp.model.StockMovementType;
import dev.wasdevv.minierp.repository.ProductRepository;
import dev.wasdevv.minierp.repository.StockMovementRepository;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Every stock change goes through here, so the ledger and {@code products.stock_quantity} never disagree.
 * Receipts and adjustments lock the product row like sales do.
 */
@Service
public class StockService {

    static final int MAX_QUANTITY = 1_000_000;
    static final int MAX_REASON = 200;

    private final ProductRepository products;
    private final StockMovementRepository movements;
    private final CurrentUser user;
    private final Clock clock;

    public StockService(ProductRepository products, StockMovementRepository movements, CurrentUser user, Clock clock) {
        this.products = products;
        this.movements = movements;
        this.user = user;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Page<StockMovement> history(Long productId, StockMovementType type, Pageable pageable) {
        return movements.history(productId, type, pageable);
    }

    /** Goods in: adds {@code quantity} units. The reason (invoice, supplier) is optional. */
    @Transactional
    public StockMovement receive(Long productId, Integer quantity, String reason) {
        if (quantity == null || quantity <= 0) {
            throw new StockRejectedException("Informe uma quantidade de entrada maior que zero.");
        }
        if (quantity > MAX_QUANTITY) {
            throw new StockRejectedException("A entrada pode ter no máximo " + MAX_QUANTITY + " unidades.");
        }
        Product product = lock(productId);
        if ((long) product.getStockQuantity() + quantity > Integer.MAX_VALUE) {
            throw new StockRejectedException("O estoque resultante ficaria grande demais.");
        }
        product.addStock(quantity);
        return record(product, StockMovementType.RECEIPT, quantity, optionalReason(reason), null);
    }

    /**
     * Inventory count: the product now holds {@code counted} units, and the ledger records the difference.
     * The reason is required, because an adjustment is the one change nothing else explains.
     */
    @Transactional
    public StockMovement adjust(Long productId, Integer counted, String reason) {
        if (counted == null || counted < 0) {
            throw new StockRejectedException("Informe a quantidade contada (zero ou mais).");
        }
        if (counted > MAX_QUANTITY) {
            throw new StockRejectedException("A contagem pode ter no máximo " + MAX_QUANTITY + " unidades.");
        }
        String why = optionalReason(reason);
        if (why == null) {
            throw new StockRejectedException("Informe o motivo do ajuste.");
        }
        Product product = lock(productId);
        int delta = counted - product.getStockQuantity();
        if (delta == 0) {
            throw new StockRejectedException("O estoque de " + product.getName() + " já é " + counted + ".");
        }
        if (delta > 0) {
            product.addStock(delta);
        } else {
            product.removeStock(-delta);
        }
        return record(product, StockMovementType.ADJUSTMENT, delta, why, null);
    }

    /** The first line of a new product's ledger; nothing is recorded for a product created with zero. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordInitial(Product product) {
        if (product.getStockQuantity() > 0) {
            record(product, StockMovementType.INITIAL, product.getStockQuantity(), null, null);
        }
    }

    /** Called by {@code SaleService} after it changed the (already locked) products' stock. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordSale(Sale sale) {
        sale.getItems().forEach(item ->
                record(item.getProduct(), StockMovementType.SALE, -item.getQuantity(), null, sale));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordCancellation(Sale sale, List<Product> restocked) {
        sale.getItems().forEach(item -> {
            Product product = restocked.stream().filter(p -> p.getId().equals(item.getProduct().getId()))
                    .findFirst().orElseThrow();
            record(product, StockMovementType.CANCELLATION, item.getQuantity(), sale.getCancelReason(), sale);
        });
    }

    /** Drops the ledger of a product that is being deleted; only callable for products never sold. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void forget(Product product) {
        movements.deleteByProduct(product);
    }

    private Product lock(Long productId) {
        return products.findAllForUpdate(List.of(productId)).stream().findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Product", productId));
    }

    private StockMovement record(Product product, StockMovementType type, int quantity, String reason, Sale sale) {
        return movements.save(new StockMovement(product, type, quantity, reason, sale, OffsetDateTime.now(clock),
                user.name()));
    }

    private static String optionalReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return null;
        }
        String why = reason.strip();
        if (why.length() > MAX_REASON) {
            throw new StockRejectedException("O motivo pode ter no máximo " + MAX_REASON + " caracteres.");
        }
        return why;
    }
}
