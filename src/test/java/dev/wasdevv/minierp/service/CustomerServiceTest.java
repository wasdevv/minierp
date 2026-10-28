package dev.wasdevv.minierp.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.wasdevv.minierp.dto.CustomerForm;
import dev.wasdevv.minierp.exception.DuplicateDocumentException;
import dev.wasdevv.minierp.exception.InUseException;
import dev.wasdevv.minierp.model.Customer;
import dev.wasdevv.minierp.repository.SaleRepository;
import dev.wasdevv.minierp.repository.CustomerRepository;
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
class CustomerServiceTest {

    @Mock
    private CustomerRepository customers;

    @Mock
    private SaleRepository sales;

    @InjectMocks
    private CustomerService service;

    @Test
    void createRejectsDocumentAlreadyInUse() {
        when(customers.existsByDocument("52998224725")).thenReturn(true);

        assertThatThrownBy(() -> service.create(form("529.982.247-25"))).isInstanceOf(DuplicateDocumentException.class);
        verify(customers, never()).saveAndFlush(any());
    }

    @Test
    void createWithoutDocumentSkipsTheDuplicateCheck() {
        when(customers.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.create(form(""));

        verify(customers, never()).existsByDocument(anyString());
    }

    @Test
    void createTurnsUniqueViolationFromConcurrentInsertIntoDuplicateDocument() {
        when(customers.existsByDocument("52998224725")).thenReturn(false);
        when(customers.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("insert",
                new SQLException("duplicate key value violates unique constraint \"customers_document_key\"")));

        assertThatThrownBy(() -> service.create(form("52998224725"))).isInstanceOf(DuplicateDocumentException.class);
    }

    @Test
    void updateRejectsDocumentOfAnotherCustomer() {
        when(customers.findById(3L)).thenReturn(Optional.of(new Customer()));
        when(customers.existsByDocumentAndIdNot("52998224725", 3L)).thenReturn(true);

        assertThatThrownBy(() -> service.update(3L, form("52998224725"))).isInstanceOf(DuplicateDocumentException.class);
    }

    @Test
    void createRethrowsOtherIntegrityViolations() {
        DataIntegrityViolationException other = new DataIntegrityViolationException("insert",
                new SQLException("new row violates check constraint \"customers_document_check\""));
        when(customers.existsByDocument("52998224725")).thenReturn(false);
        when(customers.saveAndFlush(any())).thenThrow(other);

        assertThatThrownBy(() -> service.create(form("52998224725"))).isSameAs(other);
    }

    @Test
    void deleteRefusesCustomerWithSales() {
        when(customers.findById(3L)).thenReturn(Optional.of(new Customer()));
        when(sales.existsByCustomerId(3L)).thenReturn(true);

        assertThatThrownBy(() -> service.delete(3L)).isInstanceOf(InUseException.class);
        verify(customers, never()).delete(any());
    }

    @Test
    void deleteRacingWithASaleIsInUseNotA500() {
        when(customers.findById(3L)).thenReturn(Optional.of(new Customer()));
        doThrow(new DataIntegrityViolationException("fk")).when(customers).flush();

        assertThatThrownBy(() -> service.delete(3L)).isInstanceOf(InUseException.class);
    }

    @Test
    void searchMatchesFormattedDocumentByItsDigits() {
        service.search(" 529.982.247-25 ", Pageable.unpaged());

        verify(customers).search("529.982.247-25", "52998224725", Pageable.unpaged());
    }

    @Test
    void searchWithoutDigitsPassesEmptyDocument() {
        service.search("maria_", Pageable.unpaged());

        verify(customers).search("maria\\_", "", Pageable.unpaged());
    }

    private static CustomerForm form(String document) {
        CustomerForm form = new CustomerForm();
        form.setName("Maria Souza");
        form.setDocument(document);
        return form;
    }
}
