package dev.wasdevv.minierp.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

/** One line of a product's stock ledger. Immutable: a wrong movement is corrected by another one. */
@Entity
@Table(name = "stock_movements")
public class StockMovement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false, updatable = false)
    private Product product;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private StockMovementType type;

    /** Signed: positive puts units in, negative takes them out. */
    @Column(nullable = false, updatable = false)
    private int quantity;

    @Column(name = "balance_after", nullable = false, updatable = false)
    private int balanceAfter;

    @Column(length = 200, updatable = false)
    private String reason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sale_id", updatable = false)
    private Sale sale;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "created_by", nullable = false, length = 60, updatable = false)
    private String createdBy;

    protected StockMovement() {
    }

    public StockMovement(Product product, StockMovementType type, int quantity, String reason, Sale sale,
                         OffsetDateTime createdAt, String createdBy) {
        this.product = product;
        this.type = type;
        this.quantity = quantity;
        this.balanceAfter = product.getStockQuantity();
        this.reason = reason;
        this.sale = sale;
        this.createdAt = createdAt;
        this.createdBy = createdBy;
    }

    public Long getId() {
        return id;
    }

    public Product getProduct() {
        return product;
    }

    public StockMovementType getType() {
        return type;
    }

    public int getQuantity() {
        return quantity;
    }

    public int getBalanceAfter() {
        return balanceAfter;
    }

    public String getReason() {
        return reason;
    }

    public Sale getSale() {
        return sale;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public String getCreatedBy() {
        return createdBy;
    }
}
