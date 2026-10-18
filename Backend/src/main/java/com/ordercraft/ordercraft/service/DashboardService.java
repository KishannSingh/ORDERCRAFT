package com.ordercraft.ordercraft.service;

import com.ordercraft.ordercraft.repository.CustomerRepository;
import com.ordercraft.ordercraft.repository.BOMRepository;
import com.ordercraft.ordercraft.repository.InvoiceRepository;
import com.ordercraft.ordercraft.repository.PaymentRepository;
import com.ordercraft.ordercraft.repository.ProductRepository;
import com.ordercraft.ordercraft.repository.PurchaseOrderRepository;
import com.ordercraft.ordercraft.repository.SalesOrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

@Service
public class DashboardService {

    private final CustomerRepository customerRepository;
    private final ProductRepository productRepository;
    private final BOMRepository bomRepository;
    private final SalesOrderRepository salesOrderRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final InvoiceRepository invoiceRepository;
    private final PaymentRepository paymentRepository;

    public DashboardService(
            CustomerRepository customerRepository,
            ProductRepository productRepository,
            BOMRepository bomRepository,
            SalesOrderRepository salesOrderRepository,
            PurchaseOrderRepository purchaseOrderRepository,
            InvoiceRepository invoiceRepository,
            PaymentRepository paymentRepository) {

        this.customerRepository = customerRepository;
        this.productRepository = productRepository;
        this.bomRepository = bomRepository;
        this.salesOrderRepository = salesOrderRepository;
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.invoiceRepository = invoiceRepository;
        this.paymentRepository = paymentRepository;
    }


    @Transactional(readOnly = true)
    public Map<String, Object> getDashboardData() {

        Map<String, Object> data = new HashMap<>();

        data.put("totalCustomers", customerRepository.count());
        data.put("totalProducts", productRepository.count());
        data.put("totalBoms", bomRepository.count());
        data.put("totalOrders", salesOrderRepository.count());
        data.put("totalPurchaseOrders", purchaseOrderRepository.count());
        data.put("totalInvoices", invoiceRepository.count());
        data.put("totalPayments", paymentRepository.count());


        data.put(
                "recentOrders",
                salesOrderRepository.findTop5ByOrderByOrderIdDesc()
        );

        data.put(
                "recentPayments",
                paymentRepository.findTop5ByOrderByPaymentIdDesc()
        );


        data.put(
                "paidInvoices",
                invoiceRepository.countByStatusIgnoreCase("PAID")
        );

        data.put(
                "partialInvoices",
                invoiceRepository.countByStatusContainingIgnoreCase("PARTIAL")
        );

        data.put(
                "unpaidInvoices",
                invoiceRepository.countByStatusIgnoreCase("UNPAID")
                        + invoiceRepository.countByStatusContainingIgnoreCase("PENDING")
                        + invoiceRepository.countByStatusContainingIgnoreCase("OVERDUE")
        );


        data.put(
                "pendingOrders",
                salesOrderRepository.countByStatusContainingIgnoreCase("PENDING")
                        + salesOrderRepository.countByStatusContainingIgnoreCase("DRAFT")
        );

        data.put(
                "confirmedOrders",
                salesOrderRepository.countByStatusContainingIgnoreCase("CONFIRM")
                        + salesOrderRepository.countByStatusContainingIgnoreCase("APPROVED")
        );

        data.put(
                "completedOrders",
                salesOrderRepository.countByStatusContainingIgnoreCase("COMPLETE")
                        + salesOrderRepository.countByStatusContainingIgnoreCase("DELIVERED")
        );

        data.put(
                "cancelledOrders",
                salesOrderRepository.countByStatusContainingIgnoreCase("CANCEL")
        );


        return data;
    }

}
