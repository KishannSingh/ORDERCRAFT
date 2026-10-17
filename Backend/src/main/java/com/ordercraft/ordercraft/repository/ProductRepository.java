package com.ordercraft.ordercraft.repository;

import com.ordercraft.ordercraft.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, Long> {
}