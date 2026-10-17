package com.ordercraft.ordercraft.repository;

import com.ordercraft.ordercraft.entity.SalesOrder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SalesOrderRepository extends JpaRepository<SalesOrder, Long> {

    List<SalesOrder> findTop5ByOrderByOrderIdDesc();

    long countByStatus(String status);

}