package com.ordercraft.ordercraft.repository;

import com.ordercraft.ordercraft.entity.Invoice;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

    long countByStatus(String status);

    long countByStatusIgnoreCase(String status);

    long countByStatusContainingIgnoreCase(String status);

    boolean existsByOrderId(Long orderId);

    boolean existsByOrderIdAndInvoiceIdNot(Long orderId, Long invoiceId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select invoice from Invoice invoice where invoice.invoiceId = :invoiceId")
    Optional<Invoice> findByIdForUpdate(@Param("invoiceId") Long invoiceId);

}
