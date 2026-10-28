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

    /** Name or SKU contains {@code term}, case-insensitive. */
    @Query("""
            select p from Product p
            where lower(p.name) like lower(concat('%', :term, '%')) escape '\\'
               or lower(p.sku) like lower(concat('%', :term, '%')) escape '\\'
            """)
    Page<Product> search(@Param("term") String term, Pageable pageable);
}
