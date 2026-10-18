package com.ordercraft.ordercraft;

import com.ordercraft.ordercraft.entity.SalesOrder;
import com.ordercraft.ordercraft.entity.SalesOrderItem;
import com.ordercraft.ordercraft.repository.InvoiceRepository;
import com.ordercraft.ordercraft.repository.PurchaseOrderRepository;
import com.ordercraft.ordercraft.repository.ProductRepository;
import com.ordercraft.ordercraft.repository.SalesOrderItemRepository;
import com.ordercraft.ordercraft.repository.SalesOrderRepository;
import com.ordercraft.ordercraft.service.SalesOrderItemService;
import org.junit.jupiter.api.BeforeEach;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SalesOrderItemServiceTest {

    @Mock SalesOrderItemRepository itemRepository;
    @Mock SalesOrderRepository orderRepository;
    @Mock ProductRepository productRepository;
    @Mock InvoiceRepository invoiceRepository;
    @Mock PurchaseOrderRepository purchaseOrderRepository;

    @InjectMocks SalesOrderItemService service;

    private SalesOrderItem item;

    @BeforeEach
    void setUp() {
        item = new SalesOrderItem();
        item.setOrderId(11L);
        item.setProductId(4L);
        item.setQuantity(2);
        item.setUnitPrice(19.95);
    }

    @Test
    void rejectsZeroQuantityBeforePersistence() {
        item.setQuantity(0);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.saveItem(item)
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verifyNoInteractions(itemRepository, orderRepository, productRepository, invoiceRepository, purchaseOrderRepository);
    }

    @Test
    void rejectsMissingProductWithNotFound() {
        when(productRepository.existsById(4L)).thenReturn(false);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.saveItem(item)
        );

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
    }

    @Test
    void savesLineAndRecalculatesOrderTotalFromPersistedItems() {
        SalesOrder order = new SalesOrder();
        order.setOrderId(11L);
        when(orderRepository.findByIdForUpdate(11L)).thenReturn(Optional.of(order));
        when(orderRepository.findById(11L)).thenReturn(Optional.of(order));
        when(productRepository.existsById(4L)).thenReturn(true);
        when(invoiceRepository.existsByOrderId(11L)).thenReturn(false);
        when(itemRepository.save(item)).thenReturn(item);
        when(itemRepository.findByOrderId(11L)).thenReturn(List.of(item));

        SalesOrderItem result = service.saveItem(item);

        assertEquals(item, result);
        assertEquals(39.90, order.getTotalAmount(), 0.000001);
        verify(orderRepository).save(order);
    }

    @Test
    void movingItemRecalculatesBothTheOldAndNewSalesOrderTotals() {
        SalesOrder oldOrder = new SalesOrder();
        oldOrder.setOrderId(11L);
        oldOrder.setTotalAmount(39.90);
        SalesOrder newOrder = new SalesOrder();
        newOrder.setOrderId(12L);
        SalesOrderItem existingItem = new SalesOrderItem();
        existingItem.setItemId(51L);
        existingItem.setOrderId(11L);
        existingItem.setProductId(4L);
        existingItem.setQuantity(1);
        existingItem.setUnitPrice(5.0);

        item.setItemId(51L);
        item.setOrderId(12L);
        when(itemRepository.findById(51L)).thenReturn(java.util.Optional.of(existingItem));
        when(orderRepository.findByIdForUpdate(11L)).thenReturn(java.util.Optional.of(oldOrder));
        when(orderRepository.findByIdForUpdate(12L)).thenReturn(java.util.Optional.of(newOrder));
        when(productRepository.existsById(4L)).thenReturn(true);
        when(invoiceRepository.existsByOrderId(11L)).thenReturn(false);
        when(invoiceRepository.existsByOrderId(12L)).thenReturn(false);
        when(purchaseOrderRepository.findByOrderId(11L)).thenReturn(List.of());
        when(purchaseOrderRepository.findByOrderId(12L)).thenReturn(List.of());
        when(itemRepository.save(item)).thenReturn(item);
        when(orderRepository.findById(11L)).thenReturn(java.util.Optional.of(oldOrder));
        when(orderRepository.findById(12L)).thenReturn(java.util.Optional.of(newOrder));
        when(itemRepository.findByOrderId(11L)).thenReturn(List.of());
        when(itemRepository.findByOrderId(12L)).thenReturn(List.of(item));

        service.saveItem(item);

        assertEquals(0.0, oldOrder.getTotalAmount(), 0.000001);
        assertEquals(39.90, newOrder.getTotalAmount(), 0.000001);
        verify(orderRepository).save(oldOrder);
        verify(orderRepository).save(newOrder);
    }

    @Test
    void deletingLastItemRecalculatesTheSalesOrderTotalToZero() {
        SalesOrder order = new SalesOrder();
        order.setOrderId(11L);
        order.setTotalAmount(39.90);
        item.setItemId(51L);
        when(itemRepository.findById(51L)).thenReturn(java.util.Optional.of(item));
        when(orderRepository.findByIdForUpdate(11L)).thenReturn(java.util.Optional.of(order));
        when(invoiceRepository.existsByOrderId(11L)).thenReturn(false);
        when(purchaseOrderRepository.findByOrderId(11L)).thenReturn(List.of());
        when(itemRepository.findByOrderId(11L)).thenReturn(List.of());
        when(orderRepository.findById(11L)).thenReturn(java.util.Optional.of(order));

        service.deleteItem(51L);

        assertEquals(0.0, order.getTotalAmount(), 0.000001);
        verify(itemRepository).delete(item);
        verify(orderRepository).save(order);
    }

    @Test
    void preventsChangingOrderItemsAfterInvoiceCreation() {
        when(orderRepository.findByIdForUpdate(11L)).thenReturn(Optional.of(new SalesOrder()));
        when(productRepository.existsById(4L)).thenReturn(true);
        when(invoiceRepository.existsByOrderId(11L)).thenReturn(true);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.saveItem(item)
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
    }
}
