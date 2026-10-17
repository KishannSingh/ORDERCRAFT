package com.ordercraft.ordercraft.repository;

import com.ordercraft.ordercraft.entity.BOM;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BOMRepository extends JpaRepository<BOM, Long> {

    List<BOM> findByProductId(Long productId);

}