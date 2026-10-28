package dev.wasdevv.minierp.service;

import dev.wasdevv.minierp.dto.CustomerForm;
import dev.wasdevv.minierp.exception.DuplicateDocumentException;
import dev.wasdevv.minierp.exception.ResourceNotFoundException;
import dev.wasdevv.minierp.model.Customer;
import dev.wasdevv.minierp.model.TaxId;
import dev.wasdevv.minierp.repository.CustomerRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomerService {

    private final CustomerRepository customers;

    public CustomerService(CustomerRepository customers) {
        this.customers = customers;
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

    @Transactional
    public void delete(Long id) {
        customers.delete(get(id));
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
