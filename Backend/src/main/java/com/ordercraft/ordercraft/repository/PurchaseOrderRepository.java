package com.ordercraft.ordercraft.repository;

import com.ordercraft.ordercraft.entity.PurchaseOrder;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, Long> {
    boolean existsByOrderId(Long orderId);

    boolean existsByBomId(Long bomId);

    List<PurchaseOrder> findByOrderId(Long orderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select purchaseOrder from PurchaseOrder purchaseOrder where purchaseOrder.poId = :poId")
    Optional<PurchaseOrder> findByIdForUpdate(@Param("poId") Long poId);
}
