package com.ordercraft.ordercraft.service;

import com.ordercraft.ordercraft.repository.CustomerRepository;
import com.ordercraft.ordercraft.repository.InvoiceRepository;
import com.ordercraft.ordercraft.repository.PaymentRepository;
import com.ordercraft.ordercraft.repository.ProductRepository;
import com.ordercraft.ordercraft.repository.SalesOrderRepository;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class DashboardService {

    private final CustomerRepository customerRepository;
    private final ProductRepository productRepository;
    private final SalesOrderRepository salesOrderRepository;
    private final InvoiceRepository invoiceRepository;
    private final PaymentRepository paymentRepository;

    public DashboardService(
            CustomerRepository customerRepository,
            ProductRepository productRepository,
            SalesOrderRepository salesOrderRepository,
            InvoiceRepository invoiceRepository,
            PaymentRepository paymentRepository) {

        this.customerRepository = customerRepository;
        this.productRepository = productRepository;
        this.salesOrderRepository = salesOrderRepository;
        this.invoiceRepository = invoiceRepository;
        this.paymentRepository = paymentRepository;
    }


    public Map<String, Object> getDashboardData() {

        Map<String, Object> data = new HashMap<>();

        data.put("totalCustomers", customerRepository.count());
        data.put("totalProducts", productRepository.count());
        data.put("totalOrders", salesOrderRepository.count());
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
                invoiceRepository.countByStatus("PAID")
        );

        data.put(
                "partialInvoices",
                invoiceRepository.countByStatus("PARTIAL")
        );

        data.put(
                "unpaidInvoices",
                invoiceRepository.countByStatus("UNPAID")
        );


        data.put(
                "pendingOrders",
                salesOrderRepository.countByStatus("PENDING")
        );

        data.put(
                "confirmedOrders",
                salesOrderRepository.countByStatus("CONFIRMED")
        );

        data.put(
                "completedOrders",
                salesOrderRepository.countByStatus("COMPLETED")
        );

        data.put(
                "cancelledOrders",
                salesOrderRepository.countByStatus("CANCELLED")
        );


        return data;
    }

}