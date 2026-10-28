package dev.wasdevv.minierp.repository;

import dev.wasdevv.minierp.model.Sale;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SaleRepository extends JpaRepository<Sale, Long> {

    @EntityGraph(attributePaths = "customer")
    Page<Sale> findAllBy(Pageable pageable);

    @EntityGraph(attributePaths = {"customer", "items", "items.product"})
    Optional<Sale> findWithItemsById(Long id);

    boolean existsByCustomerId(Long customerId);

    /** Sales with {@code start <= sale_date < end}, oldest first. */
    @EntityGraph(attributePaths = "customer")
    @Query("select s from Sale s where s.saleDate >= :start and s.saleDate < :end order by s.saleDate, s.id")
    List<Sale> findForReport(@Param("start") OffsetDateTime start, @Param("end") OffsetDateTime end);
}
