package dev.wasdevv.minierp.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ProductTest {

    @Test
    void removeStockSubtracts() {
        Product product = new Product();
        product.setStockQuantity(5);

        product.removeStock(5);

        assertThat(product.getStockQuantity()).isZero();
    }

    @Test
    void removeStockNeverGoesNegative() {
        Product product = new Product();
        product.setStockQuantity(2);

        assertThatThrownBy(() -> product.removeStock(3)).isInstanceOf(IllegalStateException.class);
        assertThat(product.getStockQuantity()).isEqualTo(2);
    }
}
