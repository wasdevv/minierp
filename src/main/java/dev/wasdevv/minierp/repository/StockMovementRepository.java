package dev.wasdevv.minierp.repository;

import dev.wasdevv.minierp.model.Product;
import dev.wasdevv.minierp.model.StockMovement;
import dev.wasdevv.minierp.model.StockMovementType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StockMovementRepository extends JpaRepository<StockMovement, Long> {

    @Modifying
    @Query("delete from StockMovement m where m.product = :product")
    void deleteByProduct(@Param("product") Product product);

    /** Newest first; {@code productId} and {@code type} are optional filters. */
    @EntityGraph(attributePaths = {"product", "sale"})
    @Query("""
            select m from StockMovement m
            where (:productId is null or m.product.id = :productId)
              and (:type is null or m.type = :type)
            order by m.createdAt desc, m.id desc""")
    Page<StockMovement> history(@Param("productId") Long productId, @Param("type") StockMovementType type,
                                Pageable pageable);
}
