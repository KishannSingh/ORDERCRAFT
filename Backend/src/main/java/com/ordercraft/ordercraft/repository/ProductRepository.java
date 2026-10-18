package com.ordercraft.ordercraft.repository;

import com.ordercraft.ordercraft.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, Long> {
    boolean existsByProductCodeIgnoreCase(String productCode);

    boolean existsByProductCodeIgnoreCaseAndProductIdNot(
            String productCode,
            Long productId
    );
}
