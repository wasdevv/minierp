package dev.wasdevv.minierp.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * What the sale form submits: who buys and which products in which quantities. Prices and totals are
 * deliberately absent; the server reads them from the products.
 */
public class SaleForm {

    private Long customerId;

    private List<SaleItemForm> items = new ArrayList<>();

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
