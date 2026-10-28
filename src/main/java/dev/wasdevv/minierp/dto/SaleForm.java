package dev.wasdevv.minierp.dto;

import dev.wasdevv.minierp.model.PaymentMethod;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * What the sale form submits: who buys and which products in which quantities. Prices and totals are
 * deliberately absent; the server reads them from the products.
 */
public class SaleForm {

    private Long customerId;

    private List<SaleItemForm> items = new ArrayList<>();

    private PaymentMethod paymentMethod;

    /** {@code AMOUNT} (R$) or {@code PERCENT}. */
    private String discountType = "AMOUNT";

    private BigDecimal discountValue;

    /** Cash handed over; only used with {@link PaymentMethod#CASH}, to compute the change. */
    private BigDecimal amountReceived;

    public static SaleForm withEmptyRow() {
        SaleForm form = new SaleForm();
        form.addRow();
        return form;
    }

    public void addRow() {
        items.add(new SaleItemForm());
    }

    public void removeRow(int index) {
        if (index >= 0 && index < items.size()) {
            items.remove(index);
        }
        if (items.isEmpty()) {
            addRow();
        }
    }

    public List<SaleItemForm> filledItems() {
        return items.stream().filter(item -> !item.isBlank()).toList();
    }

    public boolean isPercentDiscount() {
        return "PERCENT".equals(discountType);
    }

    public PaymentMethod getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(PaymentMethod paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

    public String getDiscountType() {
        return discountType;
    }

    public void setDiscountType(String discountType) {
        this.discountType = "PERCENT".equals(discountType) ? "PERCENT" : "AMOUNT";
    }

    public BigDecimal getDiscountValue() {
        return discountValue;
    }

    public void setDiscountValue(BigDecimal discountValue) {
        this.discountValue = discountValue;
    }

    public BigDecimal getAmountReceived() {
        return amountReceived;
    }

    public void setAmountReceived(BigDecimal amountReceived) {
        this.amountReceived = amountReceived;
    }

    public Long getCustomerId() {
        return customerId;
    }

    public void setCustomerId(Long customerId) {
        this.customerId = customerId;
    }

    public List<SaleItemForm> getItems() {
        return items;
    }

    public void setItems(List<SaleItemForm> items) {
        this.items = items == null ? new ArrayList<>() : items;
    }
}
