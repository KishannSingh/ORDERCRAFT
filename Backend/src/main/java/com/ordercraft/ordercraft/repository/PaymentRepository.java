package com.ordercraft.ordercraft.repository;

import com.ordercraft.ordercraft.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    List<Payment> findByInvoiceId(Long invoiceId);

    List<Payment> findTop5ByOrderByPaymentIdDesc();

}