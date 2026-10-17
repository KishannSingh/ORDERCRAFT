package com.ordercraft.ordercraft.repository;

import com.ordercraft.ordercraft.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerRepository extends JpaRepository<Customer, Long> {
}