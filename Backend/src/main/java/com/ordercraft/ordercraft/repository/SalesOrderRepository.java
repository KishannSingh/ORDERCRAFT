package com.ordercraft.ordercraft.repository;

import com.ordercraft.ordercraft.entity.SalesOrder;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SalesOrderRepository extends JpaRepository<SalesOrder, Long> {

    List<SalesOrder> findTop5ByOrderByOrderIdDesc();

    long countByStatus(String status);

    long countByStatusContainingIgnoreCase(String status);

    boolean existsByCustomerId(Long customerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select salesOrder from SalesOrder salesOrder where salesOrder.orderId = :orderId")
    Optional<SalesOrder> findByIdForUpdate(@Param("orderId") Long orderId);

}
