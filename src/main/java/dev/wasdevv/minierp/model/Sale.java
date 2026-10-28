package dev.wasdevv.minierp.model;

import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.annotations.Formula;

/** A sale. Sales are history: items and totals never change; a sale can only be cancelled. */
@Entity
@Table(name = "sales")
public class Sale {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** {@code null} for a walk-in customer. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @Column(name = "sale_date", nullable = false, updatable = false)
    private OffsetDateTime saleDate;

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2, updatable = false)
    private BigDecimal totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SaleStatus status = SaleStatus.CONFIRMED;

    @Column(name = "cancelled_at")
    private OffsetDateTime cancelledAt;

    @Column(name = "cancel_reason", length = 200)
    private String cancelReason;

    @OneToMany(mappedBy = "sale", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<SaleItem> items = new ArrayList<>();

    /** Units sold, summed in SQL so the list does not load every item. */
    @Formula("(select coalesce(sum(i.quantity), 0) from sale_items i where i.sale_id = id)")
    private int unitCount;

    public void addItem(SaleItem item) {
        item.setSale(this);
        items.add(item);
    }

    /** Marks the sale cancelled; putting the items back in stock is the caller's job, in the same transaction. */
    public void cancel(OffsetDateTime when, String reason) {
        if (isCancelled()) {
            throw new IllegalStateException("Sale " + id + " is already cancelled");
        }
        this.status = SaleStatus.CANCELLED;
        this.cancelledAt = when;
        this.cancelReason = reason;
    }

    public boolean isCancelled() {
        return status == SaleStatus.CANCELLED;
    }

    public SaleStatus getStatus() {
        return status;
    }

    public OffsetDateTime getCancelledAt() {
        return cancelledAt;
    }

    public String getCancelReason() {
        return cancelReason;
    }

    public Long getId() {
        return id;
    }

    public Customer getCustomer() {
        return customer;
    }

    public void setCustomer(Customer customer) {
        this.customer = customer;
    }

    public OffsetDateTime getSaleDate() {
        return saleDate;
    }

    public void setSaleDate(OffsetDateTime saleDate) {
        this.saleDate = saleDate;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    public List<SaleItem> getItems() {
        return items;
    }

    public int getUnitCount() {
        return unitCount;
    }
}
