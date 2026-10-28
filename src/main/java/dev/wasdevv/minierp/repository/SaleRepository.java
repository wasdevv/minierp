package dev.wasdevv.minierp.repository;

import dev.wasdevv.minierp.model.Sale;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SaleRepository extends JpaRepository<Sale, Long> {

    @EntityGraph(attributePaths = "customer")
    Page<Sale> findAllBy(Pageable pageable);

    @EntityGraph(attributePaths = {"customer", "items", "items.product"})
    Optional<Sale> findWithItemsById(Long id);

    boolean existsByCustomerId(Long customerId);
}
