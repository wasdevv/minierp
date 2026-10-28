package dev.wasdevv.minierp.repository;

import dev.wasdevv.minierp.dto.Dashboard;
import dev.wasdevv.minierp.model.SaleItem;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SaleItemRepository extends JpaRepository<SaleItem, Long> {

    boolean existsByProductId(Long productId);

    /** Best sellers by units among confirmed sales with {@code start <= sale_date < end}. */
    @Query("""
            select new dev.wasdevv.minierp.dto.Dashboard$TopProduct(p.name, p.sku, sum(i.quantity), sum(i.subtotal))
            from SaleItem i join i.product p join i.sale s
            where s.status = dev.wasdevv.minierp.model.SaleStatus.CONFIRMED
              and s.saleDate >= :start and s.saleDate < :end
            group by p.id, p.name, p.sku
            order by sum(i.quantity) desc, sum(i.subtotal) desc, p.name""")
    List<Dashboard.TopProduct> topProducts(@Param("start") OffsetDateTime start, @Param("end") OffsetDateTime end,
                                           Pageable pageable);
}
