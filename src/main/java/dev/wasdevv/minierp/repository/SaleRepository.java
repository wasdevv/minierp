package dev.wasdevv.minierp.repository;

import dev.wasdevv.minierp.model.Sale;
import jakarta.persistence.LockModeType;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SaleRepository extends JpaRepository<Sale, Long> {

    @EntityGraph(attributePaths = "customer")
    Page<Sale> findAllBy(Pageable pageable);

    @EntityGraph(attributePaths = {"customer", "items", "items.product"})
    Optional<Sale> findWithItemsById(Long id);

    boolean existsByCustomerId(Long customerId);

    /** A customer's sales, newest first, cancelled ones included (they are part of the history). */
    Page<Sale> findByCustomerIdOrderBySaleDateDescIdDesc(Long customerId, Pageable pageable);

    /** Count, total, fiado total and last purchase of a customer's confirmed sales. */
    @Query("""
            select count(s), coalesce(sum(s.totalAmount), 0),
                   coalesce(sum(case when s.paymentMethod = dev.wasdevv.minierp.model.PaymentMethod.ON_ACCOUNT
                                     then s.totalAmount else 0 end), 0),
                   max(s.saleDate)
            from Sale s
            where s.customer.id = :customerId and s.status = dev.wasdevv.minierp.model.SaleStatus.CONFIRMED""")
    List<Object[]> customerTotals(@Param("customerId") Long customerId);

    /** SELECT ... FOR UPDATE on the sale row: two cancellations of the same sale run one after the other. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Sale s where s.id = :id")
    Optional<Sale> findForUpdate(@Param("id") Long id);

    /** Confirmed sales with {@code start <= sale_date < end}, oldest first; cancelled sales are not revenue. */
    @EntityGraph(attributePaths = "customer")
    @Query("""
            select s from Sale s
            where s.status = dev.wasdevv.minierp.model.SaleStatus.CONFIRMED
              and s.saleDate >= :start and s.saleDate < :end
            order by s.saleDate, s.id""")
    List<Sale> findForReport(@Param("start") OffsetDateTime start, @Param("end") OffsetDateTime end);
}
