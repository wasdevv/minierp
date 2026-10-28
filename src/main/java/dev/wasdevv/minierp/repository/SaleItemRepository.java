package dev.wasdevv.minierp.repository;

import dev.wasdevv.minierp.model.SaleItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SaleItemRepository extends JpaRepository<SaleItem, Long> {

    boolean existsByProductId(Long productId);
}
