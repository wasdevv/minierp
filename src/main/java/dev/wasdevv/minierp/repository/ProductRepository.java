package dev.wasdevv.minierp.repository;

import dev.wasdevv.minierp.model.Product;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, Long> {

    boolean existsBySku(String sku);

    List<Product> findByActiveTrueOrderByNameAsc();

    /**
     * SELECT ... FOR UPDATE, in id order so that two sales locking overlapping products always lock them
     * in the same order and cannot deadlock.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.id in :ids order by p.id")
    List<Product> findAllForUpdate(@Param("ids") Collection<Long> ids);

    boolean existsBySkuAndIdNot(String sku, Long id);

    /**
     * Name or SKU contains {@code term} (case-insensitive; empty matches all), optionally narrowed to active
     * products at or below {@code lowStock} units, or to inactive products.
     */
    @Query("""
            select p from Product p
            where (lower(p.name) like lower(concat('%', :term, '%')) escape '\\'
                   or lower(p.sku) like lower(concat('%', :term, '%')) escape '\\')
              and (:onlyLowStock = false or (p.active = true and p.stockQuantity <= :lowStock))
              and (:onlyInactive = false or p.active = false)
            """)
    Page<Product> search(@Param("term") String term, @Param("onlyLowStock") boolean onlyLowStock,
                         @Param("lowStock") int lowStock, @Param("onlyInactive") boolean onlyInactive,
                         Pageable pageable);

    /** Active products at or below {@code threshold} units, emptiest first. */
    @Query("select p from Product p where p.active = true and p.stockQuantity <= :threshold order by p.stockQuantity, p.name")
    List<Product> findLowStock(@Param("threshold") int threshold, Pageable pageable);

    long countByActiveTrue();
}
