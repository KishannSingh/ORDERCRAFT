package com.ordercraft.ordercraft.repository;

import com.ordercraft.ordercraft.entity.SalesOrderItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SalesOrderItemRepository
        extends JpaRepository<SalesOrderItem, Long> {

    List<SalesOrderItem> findByOrderId(Long orderId);

}