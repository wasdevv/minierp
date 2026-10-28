package dev.wasdevv.minierp.service;

import dev.wasdevv.minierp.dto.CustomerForm;
import dev.wasdevv.minierp.dto.CustomerHistory;
import dev.wasdevv.minierp.exception.DuplicateDocumentException;
import dev.wasdevv.minierp.exception.InUseException;
import dev.wasdevv.minierp.exception.ResourceNotFoundException;
import dev.wasdevv.minierp.model.Customer;
import dev.wasdevv.minierp.model.TaxId;
import dev.wasdevv.minierp.repository.CustomerRepository;
import dev.wasdevv.minierp.repository.SaleRepository;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomerService {

    static final String IN_USE = "Este cliente tem vendas registradas e não pode ser excluído.";

    private final CustomerRepository customers;
    private final SaleRepository sales;

    public CustomerService(CustomerRepository customers, SaleRepository sales) {
        this.customers = customers;
        this.sales = sales;
    }

    @Transactional(readOnly = true)
    public Page<Customer> search(String query, Pageable pageable) {
        if (query == null || query.isBlank()) {
            return customers.findAll(pageable);
        }
        String document = TaxId.normalize(query);
        return customers.search(SearchTerms.escapeLike(query.strip()), document == null ? "" : document, pageable);
    }

    @Transactional(readOnly = true)
    public CustomerHistory history(Long id, Pageable pageable) {
        Customer customer = get(id);
        Object[] totals = sales.customerTotals(id).getFirst();
        return new CustomerHistory(customer, sales.findByCustomerIdOrderBySaleDateDescIdDesc(id, pageable),
                ((Number) totals[0]).longValue(), (BigDecimal) totals[1], (BigDecimal) totals[2],
                (OffsetDateTime) totals[3]);
    }

    @Transactional(readOnly = true)
    public Customer get(Long id) {
        return customers.findById(id).orElseThrow(() -> new ResourceNotFoundException("Customer", id));
    }

    @Transactional
    public Customer create(CustomerForm form) {
        String document = form.getDocumentDigits();
        if (document != null && customers.existsByDocument(document)) {
            throw new DuplicateDocumentException();
        }
        Customer customer = new Customer();
        form.applyTo(customer);
        return saveUnique(customer);
    }

    @Transactional
    public Customer update(Long id, CustomerForm form) {
        Customer customer = get(id);
        String document = form.getDocumentDigits();
        if (document != null && customers.existsByDocumentAndIdNot(document, id)) {
            throw new DuplicateDocumentException();
        }
        form.applyTo(customer);
        return saveUnique(customer);
    }

    /** A customer with sales stays, so the sales keep saying who bought. */
    @Transactional
    public void delete(Long id) {
        Customer customer = get(id);
        if (sales.existsByCustomerId(id)) {
            throw new InUseException(IN_USE);
        }
        try {
            customers.delete(customer);
            customers.flush();
        } catch (DataIntegrityViolationException e) {
            throw new InUseException(IN_USE);
        }
    }

    /** The exists check races with a concurrent insert; the unique constraint is what actually decides. */
    private Customer saveUnique(Customer customer) {
        try {
            return customers.saveAndFlush(customer);
        } catch (DataIntegrityViolationException e) {
            String message = e.getMostSpecificCause().getMessage();
            if (message != null && message.contains("customers_document_key")) {
                throw new DuplicateDocumentException();
            }
            throw e;
        }
    }
}
