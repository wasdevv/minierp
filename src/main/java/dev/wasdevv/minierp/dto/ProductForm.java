package dev.wasdevv.minierp.dto;

import dev.wasdevv.minierp.model.Product;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.Locale;

/**
 * What the product form submits. Setters trim text, and the SKU is stored upper-case so that
 * "abc-1" and "ABC-1" cannot both exist.
 */
public class ProductForm {

    @NotBlank(message = "Informe o nome.")
    @Size(max = 120, message = "O nome deve ter no máximo 120 caracteres.")
    private String name;

    @NotBlank(message = "Informe o SKU.")
    @Size(max = 40, message = "O SKU deve ter no máximo 40 caracteres.")
    @Pattern(regexp = "[A-Z0-9._-]*", message = "Use apenas letras, números, ponto, hífen ou sublinhado no SKU.")
    private String sku;

    @Size(max = 1000, message = "A descrição deve ter no máximo 1000 caracteres.")
    private String description;

    @NotNull(message = "Informe o preço.")
    @DecimalMin(value = "0.00", message = "O preço não pode ser negativo.")
    @Digits(integer = 10, fraction = 2, message = "Use no máximo 10 dígitos inteiros e 2 casas decimais.")
    private BigDecimal price;

    @DecimalMin(value = "0.00", message = "O custo não pode ser negativo.")
    @Digits(integer = 10, fraction = 2, message = "Use no máximo 10 dígitos inteiros e 2 casas decimais.")
    private BigDecimal costPrice;

    @NotNull(message = "Informe o estoque mínimo.")
    @PositiveOrZero(message = "O estoque mínimo não pode ser negativo.")
    private Integer minStock = Product.DEFAULT_MIN_STOCK;

    /** Only on creation; afterwards stock changes through receipts and adjustments, which keep a history. */
    @NotNull(message = "Informe o estoque inicial.", groups = OnCreate.class)
    @PositiveOrZero(message = "O estoque não pode ser negativo.", groups = OnCreate.class)
    private Integer stockQuantity = 0;

    private boolean active = true;

    /** {@link Product#getVersion()} when the edit form was rendered; {@code null} for a new product. */
    private Long version;

    public static ProductForm from(Product product) {
        ProductForm form = new ProductForm();
        form.setName(product.getName());
        form.setSku(product.getSku());
        form.setDescription(product.getDescription());
        form.setPrice(product.getPrice());
        form.setStockQuantity(product.getStockQuantity());
        form.setActive(product.isActive());
        form.setCostPrice(product.getCostPrice());
        form.setMinStock(product.getMinStock());
        form.setVersion(product.getVersion());
        return form;
    }

    /** Copies everything but the stock, which only {@link #initialStock()} sets, on creation. */
    public void applyTo(Product product) {
        product.setName(name);
        product.setSku(sku);
        product.setDescription(description);
        product.setPrice(price);
        product.setCostPrice(costPrice);
        product.setMinStock(minStock);
        product.setActive(active);
    }

    public int initialStock() {
        return stockQuantity == null ? 0 : stockQuantity;
    }

    /** Validation group of the fields that only the creation form has. */
    public interface OnCreate {
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = trimToNull(name);
    }

    public String getSku() {
        return sku;
    }

    public void setSku(String sku) {
        String trimmed = trimToNull(sku);
        this.sku = trimmed == null ? null : trimmed.toUpperCase(Locale.ROOT);
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = trimToNull(description);
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public Integer getStockQuantity() {
        return stockQuantity;
    }

    public void setStockQuantity(Integer stockQuantity) {
        this.stockQuantity = stockQuantity;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public BigDecimal getCostPrice() {
        return costPrice;
    }

    public void setCostPrice(BigDecimal costPrice) {
        this.costPrice = costPrice;
    }

    public Integer getMinStock() {
        return minStock;
    }

    public void setMinStock(Integer minStock) {
        this.minStock = minStock;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.strip();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
