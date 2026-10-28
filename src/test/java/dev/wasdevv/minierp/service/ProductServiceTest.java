package dev.wasdevv.minierp.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.wasdevv.minierp.dto.ProductForm;
import dev.wasdevv.minierp.exception.DuplicateSkuException;
import dev.wasdevv.minierp.exception.ResourceNotFoundException;
import dev.wasdevv.minierp.model.Product;
import dev.wasdevv.minierp.repository.ProductRepository;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository products;

    @InjectMocks
    private ProductService service;

    @Test
    void createRejectsSkuAlreadyInUse() {
        when(products.existsBySku("CN-01")).thenReturn(true);

        assertThatThrownBy(() -> service.create(form("CN-01"))).isInstanceOf(DuplicateSkuException.class);
        verify(products, never()).saveAndFlush(any());
    }

    @Test
    void createTurnsUniqueViolationFromConcurrentInsertIntoDuplicateSku() {
        when(products.existsBySku("CN-01")).thenReturn(false);
        when(products.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("insert",
                new SQLException("duplicate key value violates unique constraint \"products_sku_key\"")));

        assertThatThrownBy(() -> service.create(form("CN-01"))).isInstanceOf(DuplicateSkuException.class);
    }

    @Test
    void createRethrowsOtherIntegrityViolations() {
        when(products.existsBySku("CN-01")).thenReturn(false);
        DataIntegrityViolationException other = new DataIntegrityViolationException("insert",
                new SQLException("new row violates check constraint \"products_price_check\""));
        when(products.saveAndFlush(any())).thenThrow(other);

        assertThatThrownBy(() -> service.create(form("CN-01"))).isSameAs(other);
    }

    @Test
    void updateAllowsKeepingTheSameSku() {
        Product existing = new Product();
        when(products.findById(7L)).thenReturn(Optional.of(existing));
        when(products.existsBySkuAndIdNot("CN-01", 7L)).thenReturn(false);
        when(products.saveAndFlush(existing)).thenReturn(existing);

        service.update(7L, form("CN-01"));

        assertThat(existing.getSku()).isEqualTo("CN-01");
        assertThat(existing.getPrice()).isEqualByComparingTo("2.50");
    }

    @Test
    void updateRejectsSkuOfAnotherProduct() {
        when(products.findById(7L)).thenReturn(Optional.of(new Product()));
        when(products.existsBySkuAndIdNot("CN-02", 7L)).thenReturn(true);

        assertThatThrownBy(() -> service.update(7L, form("CN-02"))).isInstanceOf(DuplicateSkuException.class);
    }

    @Test
    void getMissingProductThrowsNotFound() {
        when(products.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(99L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void searchEscapesLikeWildcards() {
        service.search("  50%_off\\ ", Pageable.unpaged());

        verify(products).search("50\\%\\_off\\\\", Pageable.unpaged());
    }

    @Test
    void blankSearchListsEverything() {
        service.search("  ", Pageable.unpaged());

        verify(products).findAll(Pageable.unpaged());
    }

    private static ProductForm form(String sku) {
        ProductForm form = new ProductForm();
        form.setName("Caneta azul");
        form.setSku(sku);
        form.setPrice(new BigDecimal("2.50"));
        form.setStockQuantity(10);
        return form;
    }
}
