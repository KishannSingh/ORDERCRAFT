package com.ordercraft.ordercraft;

import com.ordercraft.ordercraft.repository.BOMRepository;
import com.ordercraft.ordercraft.repository.CustomerRepository;
import com.ordercraft.ordercraft.repository.InvoiceRepository;
import com.ordercraft.ordercraft.repository.PaymentRepository;
import com.ordercraft.ordercraft.repository.ProductRepository;
import com.ordercraft.ordercraft.repository.PurchaseOrderRepository;
import com.ordercraft.ordercraft.repository.SalesOrderRepository;
import com.ordercraft.ordercraft.service.DashboardService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock CustomerRepository customerRepository;
    @Mock ProductRepository productRepository;
    @Mock BOMRepository bomRepository;
    @Mock SalesOrderRepository salesOrderRepository;
    @Mock PurchaseOrderRepository purchaseOrderRepository;
    @Mock InvoiceRepository invoiceRepository;
    @Mock PaymentRepository paymentRepository;

    @InjectMocks DashboardService service;

    @Test
    void summarizesAllWorkflowModulesAndStatusAliases() {
        when(customerRepository.count()).thenReturn(8L);
        when(productRepository.count()).thenReturn(5L);
        when(bomRepository.count()).thenReturn(4L);
        when(salesOrderRepository.count()).thenReturn(6L);
        when(purchaseOrderRepository.count()).thenReturn(2L);
        when(invoiceRepository.count()).thenReturn(3L);
        when(paymentRepository.count()).thenReturn(7L);
        when(invoiceRepository.countByStatusIgnoreCase(anyString())).thenAnswer(invocation ->
                Map.of("PAID", 1L, "UNPAID", 1L).getOrDefault(invocation.getArgument(0), 0L));
        when(invoiceRepository.countByStatusContainingIgnoreCase(anyString())).thenAnswer(invocation ->
                Map.of("PARTIAL", 1L).getOrDefault(invocation.getArgument(0), 0L));
        when(salesOrderRepository.countByStatusContainingIgnoreCase(anyString())).thenAnswer(invocation ->
                Map.of("PENDING", 2L, "CONFIRM", 1L, "COMPLETE", 2L, "CANCEL", 1L)
                        .getOrDefault(invocation.getArgument(0), 0L));

        Map<String, Object> summary = service.getDashboardData();

        assertEquals(8L, summary.get("totalCustomers"));
        assertEquals(5L, summary.get("totalProducts"));
        assertEquals(4L, summary.get("totalBoms"));
        assertEquals(6L, summary.get("totalOrders"));
        assertEquals(2L, summary.get("totalPurchaseOrders"));
        assertEquals(3L, summary.get("totalInvoices"));
        assertEquals(7L, summary.get("totalPayments"));
        assertEquals(1L, summary.get("paidInvoices"));
        assertEquals(1L, summary.get("partialInvoices"));
        assertEquals(1L, summary.get("unpaidInvoices"));
        assertEquals(2L, summary.get("pendingOrders"));
        assertEquals(1L, summary.get("confirmedOrders"));
        assertEquals(2L, summary.get("completedOrders"));
        assertEquals(1L, summary.get("cancelledOrders"));
        assertEquals(List.of(), summary.get("recentOrders"));
        assertEquals(List.of(), summary.get("recentPayments"));
    }
}
