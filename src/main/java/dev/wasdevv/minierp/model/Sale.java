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

    /** Sum of the items, before discount. */
    @Column(name = "subtotal_amount", nullable = false, precision = 12, scale = 2, updatable = false)
    private BigDecimal subtotalAmount;

    @Column(name = "discount_amount", nullable = false, precision = 12, scale = 2, updatable = false)
    private BigDecimal discountAmount = BigDecimal.ZERO;

    /** What the customer pays: subtotal minus discount. Reports and the dashboard read this. */
    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2, updatable = false)
    private BigDecimal totalAmount;

    /** {@code null} only for sales recorded before payment methods existed. */
    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", length = 20, updatable = false)
    private PaymentMethod paymentMethod;

    /** Cash handed over, when the operator typed it; {@link #changeAmount} is what went back. */
    @Column(name = "amount_received", precision = 12, scale = 2, updatable = false)
    private BigDecimal amountReceived;

    @Column(name = "change_amount", precision = 12, scale = 2, updatable = false)
    private BigDecimal changeAmount;

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


    /** Sets subtotal, discount and total together, so they can never disagree. */
    public void price(BigDecimal subtotal, BigDecimal discount) {
        this.subtotalAmount = subtotal;
        this.discountAmount = discount;
        this.totalAmount = subtotal.subtract(discount);
    }

    public void pay(PaymentMethod method, BigDecimal received) {
        this.paymentMethod = method;
        this.amountReceived = received;
        this.changeAmount = received == null ? null : received.subtract(totalAmount);
    }

    public BigDecimal getSubtotalAmount() {
        return subtotalAmount;
    }

    public BigDecimal getDiscountAmount() {
        return discountAmount;
    }

    public boolean hasDiscount() {
        return discountAmount.signum() > 0;
    }

    public PaymentMethod getPaymentMethod() {
        return paymentMethod;
    }

    public BigDecimal getAmountReceived() {
        return amountReceived;
    }

    public BigDecimal getChangeAmount() {
        return changeAmount;
    }

    public List<SaleItem> getItems() {
        return items;
    }

    public int getUnitCount() {
        return unitCount;
    }
}
