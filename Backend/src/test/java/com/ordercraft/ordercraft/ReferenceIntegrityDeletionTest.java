package com.ordercraft.ordercraft;

import com.ordercraft.ordercraft.entity.Product;
import com.ordercraft.ordercraft.entity.SalesOrder;
import com.ordercraft.ordercraft.repository.BOMRepository;
import com.ordercraft.ordercraft.repository.CustomerRepository;
import com.ordercraft.ordercraft.repository.InvoiceRepository;
import com.ordercraft.ordercraft.repository.ProductRepository;
import com.ordercraft.ordercraft.repository.PurchaseOrderRepository;
import com.ordercraft.ordercraft.repository.SalesOrderItemRepository;
import com.ordercraft.ordercraft.repository.SalesOrderRepository;
import com.ordercraft.ordercraft.service.ProductService;
import com.ordercraft.ordercraft.service.SalesOrderService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReferenceIntegrityDeletionTest {

    @Mock ProductRepository productRepository;
    @Mock BOMRepository bomRepository;
    @Mock SalesOrderItemRepository salesOrderItemRepository;
    @Mock SalesOrderRepository salesOrderRepository;
    @Mock CustomerRepository customerRepository;
    @Mock InvoiceRepository invoiceRepository;
    @Mock PurchaseOrderRepository purchaseOrderRepository;

    @InjectMocks ProductService productService;
    @InjectMocks SalesOrderService salesOrderService;

    @Test
    void doesNotDeleteProductReferencedByBom() {
        Product product = new Product();
        product.setProductId(2L);
        when(productRepository.findById(2L)).thenReturn(Optional.of(product));
        when(bomRepository.existsByProductId(2L)).thenReturn(true);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> productService.deleteProduct(2L)
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        verify(productRepository, never()).delete(any(Product.class));
    }

    @Test
    void doesNotDeleteSalesOrderReferencedByInvoice() {
        SalesOrder order = new SalesOrder();
        order.setOrderId(1L);
        when(salesOrderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(salesOrderItemRepository.findByOrderId(1L)).thenReturn(List.of());
        when(invoiceRepository.existsByOrderId(1L)).thenReturn(true);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> salesOrderService.deleteOrder(1L)
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        verify(salesOrderRepository, never()).delete(any(SalesOrder.class));
    }
}
