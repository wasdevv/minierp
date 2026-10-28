package dev.wasdevv.minierp.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "products")
public class Product {

    /** Minimum stock of a new product until the operator sets its own. */
    public static final int DEFAULT_MIN_STOCK = 10;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, length = 40, unique = true)
    private String sku;

    @Column(length = 1000)
    private String description;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Column(name = "stock_quantity", nullable = false)
    private int stockQuantity;

    /** {@code null} when nobody told us: then the margin is unknown, not 100%. */
    @Column(name = "cost_price", precision = 12, scale = 2)
    private BigDecimal costPrice;

    /** At or below this quantity an active product is low on stock. */
    @Column(name = "min_stock", nullable = false)
    private int minStock = DEFAULT_MIN_STOCK;

    @Column(nullable = false)
    private boolean active = true;

    @Version
    @Column(nullable = false)
    private long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSku() {
        return sku;
    }

    public void setSku(String sku) {
        this.sku = sku;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public int getStockQuantity() {
        return stockQuantity;
    }

    public void setStockQuantity(int stockQuantity) {
        this.stockQuantity = stockQuantity;
    }

    public BigDecimal getCostPrice() {
        return costPrice;
    }

    public void setCostPrice(BigDecimal costPrice) {
        this.costPrice = costPrice;
    }

    public int getMinStock() {
        return minStock;
    }

    public void setMinStock(int minStock) {
        this.minStock = minStock;
    }

    public boolean isLowStock() {
        return active && stockQuantity <= minStock;
    }

    /** Gross margin over the sale price, in percent; {@code null} without a cost or with a free product. */
    public BigDecimal getMarginPercent() {
        if (costPrice == null || price == null || price.signum() == 0) {
            return null;
        }
        return price.subtract(costPrice).multiply(BigDecimal.valueOf(100)).divide(price, 1, RoundingMode.HALF_UP);
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public long getVersion() {
        return version;
    }

    /** Takes {@code quantity} units out of stock; the caller has already checked there are enough. */
    public void removeStock(int quantity) {
        if (quantity > stockQuantity) {
            throw new IllegalStateException("Not enough stock for product " + id);
        }
        stockQuantity -= quantity;
    }

    /** Puts {@code quantity} units back, e.g. when a sale is cancelled. */
    public void addStock(int quantity) {
        stockQuantity = Math.addExact(stockQuantity, quantity);
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
