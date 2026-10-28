package dev.wasdevv.minierp.repository;

import dev.wasdevv.minierp.model.Customer;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

    boolean existsByDocument(String document);

    List<Customer> findAllByOrderByNameAsc();

    boolean existsByDocumentAndIdNot(String document, Long id);

    /**
     * Name or e-mail contains {@code term}, case-insensitive, or the document equals {@code document}.
     * The document match is exact on purpose: a partial match would let the search box enumerate CPFs.
     * Pass an empty {@code document} to skip it; stored documents are never empty.
     */
    @Query("""
            select c from Customer c
            where lower(c.name) like lower(concat('%', :term, '%')) escape '\\'
               or lower(c.email) like lower(concat('%', :term, '%')) escape '\\'
               or c.document = :document
            """)
    Page<Customer> search(@Param("term") String term, @Param("document") String document, Pageable pageable);
}
