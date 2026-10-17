package com.ordercraft.ordercraft.repository;

import com.ordercraft.ordercraft.entity.Invoice;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

    long countByStatus(String status);

}