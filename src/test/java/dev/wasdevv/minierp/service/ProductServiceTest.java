package dev.wasdevv.minierp.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.wasdevv.minierp.dto.ProductFilter;
import dev.wasdevv.minierp.dto.ProductForm;
import dev.wasdevv.minierp.exception.DuplicateSkuException;
import dev.wasdevv.minierp.exception.InUseException;
import dev.wasdevv.minierp.exception.ResourceNotFoundException;
import dev.wasdevv.minierp.exception.StaleProductException;
import dev.wasdevv.minierp.model.Product;
import dev.wasdevv.minierp.repository.SaleItemRepository;
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
import org.springframework.orm.ObjectOptimisticLockingFailureException;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository products;

    @Mock
    private SaleItemRepository saleItems;

    @Mock
    private StockService stock;

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

        service.update(7L, form("CN-01", 0L));

        assertThat(existing.getSku()).isEqualTo("CN-01");
        assertThat(existing.getPrice()).isEqualByComparingTo("2.50");
    }

    @Test
    void updateRejectsSkuOfAnotherProduct() {
        when(products.findById(7L)).thenReturn(Optional.of(new Product()));
        when(products.existsBySkuAndIdNot("CN-02", 7L)).thenReturn(true);

        assertThatThrownBy(() -> service.update(7L, form("CN-02", 0L))).isInstanceOf(DuplicateSkuException.class);
    }

    @Test
    void updateRejectsFormLoadedBeforeTheLastChange() {
        when(products.findById(7L)).thenReturn(Optional.of(new Product()));

        assertThatThrownBy(() -> service.update(7L, form("CN-01", 3L))).isInstanceOf(StaleProductException.class);
        assertThatThrownBy(() -> service.update(7L, form("CN-01", null))).isInstanceOf(StaleProductException.class);
        verify(products, never()).saveAndFlush(any());
    }

    @Test
    void concurrentEditCaughtAtFlushIsStale() {
        Product existing = new Product();
        when(products.findById(7L)).thenReturn(Optional.of(existing));
        when(products.existsBySkuAndIdNot("CN-01", 7L)).thenReturn(false);
        when(products.saveAndFlush(existing)).thenThrow(new ObjectOptimisticLockingFailureException(Product.class, 7L));

        assertThatThrownBy(() -> service.update(7L, form("CN-01", 0L))).isInstanceOf(StaleProductException.class);
    }

    @Test
    void deleteRefusesSoldProduct() {
        when(products.findById(7L)).thenReturn(Optional.of(new Product()));
        when(saleItems.existsByProductId(7L)).thenReturn(true);

        assertThatThrownBy(() -> service.delete(7L)).isInstanceOf(InUseException.class);
        verify(products, never()).delete(any());
    }

    @Test
    void deleteRacingWithASaleIsInUseNotA500() {
        when(products.findById(7L)).thenReturn(Optional.of(new Product()));
        doThrow(new DataIntegrityViolationException("fk")).when(products).flush();

        assertThatThrownBy(() -> service.delete(7L)).isInstanceOf(InUseException.class);
    }

    @Test
    void getMissingProductThrowsNotFound() {
        when(products.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(99L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void searchEscapesLikeWildcards() {
        service.search("  50%_off\\ ", ProductFilter.ALL, Pageable.unpaged());

        verify(products).search("50\\%\\_off\\\\", false, false, Pageable.unpaged());
    }

    @Test
    void filterWithoutSearchTermStillFilters() {
        service.search(null, ProductFilter.LOW_STOCK, Pageable.unpaged());

        verify(products).search("", true, false, Pageable.unpaged());
    }

    @Test
    void unknownFilterParamMeansAll() {
        assertThat(ProductFilter.fromParam("drop table")).isEqualTo(ProductFilter.ALL);
        assertThat(ProductFilter.fromParam(null)).isEqualTo(ProductFilter.ALL);
        assertThat(ProductFilter.fromParam("inactive")).isEqualTo(ProductFilter.INACTIVE);
    }

    @Test
    void blankSearchListsEverything() {
        service.search("  ", ProductFilter.ALL, Pageable.unpaged());

        verify(products).findAll(Pageable.unpaged());
    }

    private static ProductForm form(String sku) {
        return form(sku, null);
    }

    private static ProductForm form(String sku, Long version) {
        ProductForm form = new ProductForm();
        form.setVersion(version);
        form.setName("Caneta azul");
        form.setSku(sku);
        form.setPrice(new BigDecimal("2.50"));
        form.setStockQuantity(10);
        return form;
    }
}
