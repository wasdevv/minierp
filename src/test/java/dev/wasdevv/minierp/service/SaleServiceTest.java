package dev.wasdevv.minierp.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.wasdevv.minierp.dto.SaleForm;
import dev.wasdevv.minierp.dto.SaleItemForm;
import dev.wasdevv.minierp.exception.SaleRejectedException;
import dev.wasdevv.minierp.model.PaymentMethod;
import dev.wasdevv.minierp.model.Product;
import dev.wasdevv.minierp.model.Sale;
import dev.wasdevv.minierp.repository.CustomerRepository;
import dev.wasdevv.minierp.repository.ProductRepository;
import dev.wasdevv.minierp.repository.SaleRepository;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SaleServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-27T15:30:00Z");

    @Mock
    private SaleRepository sales;

    @Mock
    private ProductRepository products;

    @Mock
    private CustomerRepository customers;

    @Mock
    private StockService stock;

    private SaleService service;

    @BeforeEach
    void setUp() {
        service = new SaleService(sales, products, customers, stock, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void computesSubtotalsAndTotalFromCurrentPrices() {
        when(products.findAllForUpdate(anyCollection())).thenReturn(List.of(
                product(1L, "Caneta", "2.50", true), product(2L, "Caderno", "14.90", true)));
        when(sales.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Sale sale = service.create(form(null, line(1L, 4), line(2L, 3)));

        assertThat(sale.getItems()).extracting(i -> i.getSubtotal().toPlainString()).containsExactly("10.00", "44.70");
        assertThat(sale.getTotalAmount()).isEqualByComparingTo("54.70");
        assertThat(sale.getSaleDate()).isEqualTo(OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC));
        assertThat(sale.getCustomer()).isNull();
    }

    @Test
    void mergesLinesOfTheSameProduct() {
        when(products.findAllForUpdate(anyCollection())).thenReturn(List.of(product(1L, "Caneta", "2.50", true)));
        when(sales.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Sale sale = service.create(form(null, line(1L, 2), line(1L, 3)));

        assertThat(sale.getItems()).singleElement().satisfies(item -> assertThat(item.getQuantity()).isEqualTo(5));
        assertThat(sale.getTotalAmount()).isEqualByComparingTo("12.50");
    }

    @Test
    void ignoresBlankRows() {
        when(products.findAllForUpdate(anyCollection())).thenReturn(List.of(product(1L, "Caneta", "2.50", true)));
        when(sales.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Sale sale = service.create(form(null, new SaleItemForm(), line(1L, 1), new SaleItemForm()));

        assertThat(sale.getItems()).hasSize(1);
    }

    @Test
    void rejectsSaleWithoutItems() {
        assertThatThrownBy(() -> service.create(form(null, new SaleItemForm())))
                .isInstanceOf(SaleRejectedException.class)
                .hasMessageContaining("Adicione ao menos um item");
        verify(sales, never()).save(any());
    }

    @Test
    void rejectsZeroNegativeMissingAndHugeQuantitiesNamingTheLine() {
        SaleRejectedException e = rejected(form(null, line(1L, 0), line(2L, -1), line(3L, null),
                line(4L, SaleService.MAX_QUANTITY + 1), new SaleItemForm(null, 1)));

        assertThat(e.getReasons()).containsExactly(
                "Item 1: a quantidade deve ser maior que zero.",
                "Item 2: a quantidade deve ser maior que zero.",
                "Item 3: informe a quantidade.",
                "Item 4: a quantidade máxima por item é 100000.",
                "Item 5: escolha o produto.");
    }

    @Test
    void rejectsMoreLinesThanTheLimit() {
        SaleItemForm[] lines = new SaleItemForm[SaleService.MAX_LINES + 1];
        for (int i = 0; i < lines.length; i++) {
            lines[i] = line((long) i + 1, 1);
        }

        assertThat(rejected(form(null, lines)).getReasons()).contains("Uma venda pode ter no máximo 50 itens.");
    }

    @Test
    void rejectsRepeatedLinesWhoseSumPassesTheQuantityLimit() {
        assertThat(rejected(form(null, line(1L, 60_000), line(1L, 60_000))).getReasons())
                .containsExactly("A quantidade somada de um mesmo produto passa de 100000.");
    }

    @Test
    void rejectsInactiveAndMissingProducts() {
        when(products.findAllForUpdate(anyCollection())).thenReturn(List.of(product(1L, "Caneta", "2.50", false)));

        SaleRejectedException e = rejected(form(null, line(1L, 1), line(99L, 1)));

        assertThat(e.getReasons()).containsExactly(
                "O produto Caneta está inativo e não pode ser vendido.",
                "Um dos produtos escolhidos não existe mais.");
        verify(sales, never()).save(any());
    }

    @Test
    void rejectsMissingCustomer() {
        when(products.findAllForUpdate(anyCollection())).thenReturn(List.of(product(1L, "Caneta", "2.50", true)));
        when(customers.findById(7L)).thenReturn(Optional.empty());

        assertThat(rejected(form(7L, line(1L, 1))).getReasons()).containsExactly("O cliente escolhido não existe mais.");
    }

    @Test
    void rejectsTotalThatDoesNotFitTheColumn() {
        when(products.findAllForUpdate(anyCollection())).thenReturn(List.of(product(1L, "Iate", "9999999999.99", true)));

        assertThat(rejected(form(null, line(1L, 2))).getReasons())
                .containsExactly("O total da venda passa do limite de R$ 9.999.999.999,99.");
    }

    @Test
    void takesSoldUnitsOutOfStock() {
        Product pen = product(1L, "Caneta", "2.50", true);
        when(products.findAllForUpdate(anyCollection())).thenReturn(List.of(pen));
        when(sales.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.create(form(null, line(1L, 30)));

        assertThat(pen.getStockQuantity()).isEqualTo(70);
    }

    @Test
    void sellingTheWholeStockIsAllowed() {
        Product pen = product(1L, "Caneta", "2.50", true);
        when(products.findAllForUpdate(anyCollection())).thenReturn(List.of(pen));
        when(sales.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.create(form(null, line(1L, 100)));

        assertThat(pen.getStockQuantity()).isZero();
    }

    @Test
    void rejectsQuantityAboveStockAndTouchesNoStock() {
        Product pen = product(1L, "Caneta", "2.50", true);
        Product notebook = product(2L, "Caderno", "14.90", true);
        notebook.setStockQuantity(3);
        when(products.findAllForUpdate(anyCollection())).thenReturn(List.of(pen, notebook));

        assertThat(rejected(form(null, line(1L, 10), line(2L, 5))).getReasons())
                .containsExactly("Estoque insuficiente para Caderno: disponível 3, solicitado 5.");
        assertThat(pen.getStockQuantity()).as("valid line is not decremented either").isEqualTo(100);
        assertThat(notebook.getStockQuantity()).isEqualTo(3);
        verify(sales, never()).save(any());
    }

    @Test
    void checksStockAgainstTheSumOfRepeatedLines() {
        Product notebook = product(2L, "Caderno", "14.90", true);
        notebook.setStockQuantity(5);
        when(products.findAllForUpdate(anyCollection())).thenReturn(List.of(notebook));

        assertThat(rejected(form(null, line(2L, 3), line(2L, 3))).getReasons())
                .containsExactly("Estoque insuficiente para Caderno: disponível 5, solicitado 6.");
    }

    @Test
    void estimateSkipsIncompleteLines() {
        when(products.findAllById(anyCollection())).thenReturn(List.of(product(1L, "Caneta", "2.50", true)));

        BigDecimal total = service.estimateTotal(form(null, line(1L, 2), line(1L, null), line(null, 3)));

        assertThat(total).isEqualByComparingTo("5.00");
    }

    private SaleRejectedException rejected(SaleForm form) {
        try {
            service.create(form);
        } catch (SaleRejectedException e) {
            return e;
        }
        throw new AssertionError("sale was not rejected");
    }

    private static SaleForm form(Long customerId, SaleItemForm... lines) {
        SaleForm form = new SaleForm();
        form.setPaymentMethod(PaymentMethod.PIX);
        form.setCustomerId(customerId);
        form.setItems(new ArrayList<>(List.of(lines)));
        return form;
    }

    private static SaleItemForm line(Long productId, Integer quantity) {
        return new SaleItemForm(productId, quantity);
    }

    private static Product product(Long id, String name, String price, boolean active) {
        Product product = new Product();
        product.setName(name);
        product.setSku("SKU-" + id);
        product.setPrice(new BigDecimal(price));
        product.setActive(active);
        product.setStockQuantity(100);
        try {
            Field field = Product.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(product, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        return product;
    }
}
