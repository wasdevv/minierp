package dev.wasdevv.minierp.dto;

/** One line of the sale form. A line with neither product nor quantity is an unused row and is ignored. */
public class SaleItemForm {

    private Long productId;

    private Integer quantity;

    public SaleItemForm() {
    }

    public SaleItemForm(Long productId, Integer quantity) {
        this.productId = productId;
        this.quantity = quantity;
    }

    public boolean isBlank() {
        return productId == null && quantity == null;
    }

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }
}
