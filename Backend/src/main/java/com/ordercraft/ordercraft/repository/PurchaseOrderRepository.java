package com.ordercraft.ordercraft.repository;

import com.ordercraft.ordercraft.entity.PurchaseOrder;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, Long> {
}