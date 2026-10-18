package com.ordercraft.ordercraft;

import com.ordercraft.ordercraft.entity.BOM;
import com.ordercraft.ordercraft.entity.PurchaseOrder;
import com.ordercraft.ordercraft.entity.SalesOrder;
import com.ordercraft.ordercraft.entity.SalesOrderItem;
import com.ordercraft.ordercraft.repository.BOMRepository;
import com.ordercraft.ordercraft.repository.PurchaseOrderRepository;
import com.ordercraft.ordercraft.repository.SalesOrderItemRepository;
import com.ordercraft.ordercraft.repository.SalesOrderRepository;
import com.ordercraft.ordercraft.service.PurchaseOrderService;
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
class PurchaseOrderServiceTest {

    @Mock PurchaseOrderRepository purchaseOrderRepository;
    @Mock SalesOrderRepository salesOrderRepository;
    @Mock SalesOrderItemRepository salesOrderItemRepository;
    @Mock BOMRepository bomRepository;

    @InjectMocks PurchaseOrderService service;

    @Test
    void createsProcurementForMatchingOrderDemandAndCanonicalizesMaterialAndStatus() {
        when(salesOrderRepository.findByIdForUpdate(3L)).thenReturn(Optional.of(order(3L)));
        when(salesOrderItemRepository.findByOrderId(3L)).thenReturn(List.of(orderItem(3L, 14L, 3)));
        when(bomRepository.findById(8L)).thenReturn(Optional.of(bom(8L, 14L, 2.0)));
        when(purchaseOrderRepository.findByOrderId(3L)).thenReturn(List.of());
        when(purchaseOrderRepository.save(any(PurchaseOrder.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PurchaseOrder request = purchaseOrder(3L, 8L, 6.0, "pending");
        request.setMaterialName("  ");

        PurchaseOrder saved = service.savePurchaseOrder(request);

        assertEquals("Aluminum", saved.getMaterialName());
        assertEquals("PENDING", saved.getStatus());
        assertEquals(6.0, saved.getQuantity());
        verify(purchaseOrderRepository).save(request);
    }

    @Test
    void rejectsProcurementAboveRemainingMaterialDemand() {
        when(salesOrderRepository.findByIdForUpdate(3L)).thenReturn(Optional.of(order(3L)));
        when(salesOrderItemRepository.findByOrderId(3L)).thenReturn(List.of(orderItem(3L, 14L, 3)));
        when(bomRepository.findById(8L)).thenReturn(Optional.of(bom(8L, 14L, 2.0)));
        when(purchaseOrderRepository.findByOrderId(3L))
                .thenReturn(List.of(purchaseOrder(3L, 8L, 4.0, "ORDERED")));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.savePurchaseOrder(purchaseOrder(3L, 8L, 3.0, "PENDING"))
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verify(purchaseOrderRepository, never()).save(any());
    }

    @Test
    void rejectsBomForProductNotPresentOnSalesOrder() {
        when(salesOrderRepository.findByIdForUpdate(3L)).thenReturn(Optional.of(order(3L)));
        when(salesOrderItemRepository.findByOrderId(3L)).thenReturn(List.of(orderItem(3L, 14L, 3)));
        when(bomRepository.findById(8L)).thenReturn(Optional.of(bom(8L, 99L, 2.0)));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.savePurchaseOrder(purchaseOrder(3L, 8L, 1.0, "PENDING"))
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verify(purchaseOrderRepository, never()).save(any());
    }

    @Test
    void returnsNotFoundForUnknownSalesOrder() {
        when(salesOrderRepository.findByIdForUpdate(404L)).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.savePurchaseOrder(purchaseOrder(404L, 8L, 1.0, "PENDING"))
        );

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        verify(salesOrderItemRepository, never()).findByOrderId(any());
    }

    @Test
    void updatesExistingPurchaseOrderAndExcludesItFromAlreadyProcuredDemand() {
        PurchaseOrder existing = purchaseOrder(3L, 8L, 4.0, "PENDING");
        existing.setPoId(22L);
        when(purchaseOrderRepository.findByIdForUpdate(22L)).thenReturn(Optional.of(existing));
        when(salesOrderRepository.findByIdForUpdate(3L)).thenReturn(Optional.of(order(3L)));
        when(salesOrderItemRepository.findByOrderId(3L)).thenReturn(List.of(orderItem(3L, 14L, 3)));
        when(bomRepository.findById(8L)).thenReturn(Optional.of(bom(8L, 14L, 2.0)));
        when(purchaseOrderRepository.findByOrderId(3L)).thenReturn(List.of(existing));
        when(purchaseOrderRepository.save(any(PurchaseOrder.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PurchaseOrder update = purchaseOrder(3L, 8L, 5.0, "ordered");
        update.setPoId(22L);

        PurchaseOrder saved = service.savePurchaseOrder(update);

        assertEquals(22L, saved.getPoId());
        assertEquals(5.0, saved.getQuantity());
        assertEquals("ORDERED", saved.getStatus());
        assertEquals("Aluminum", saved.getMaterialName());
        verify(purchaseOrderRepository).save(update);
    }

    @Test
    void readsPurchaseOrderListAndDetailsFromRepository() {
        PurchaseOrder existing = purchaseOrder(3L, 8L, 4.0, "PENDING");
        existing.setPoId(22L);
        when(purchaseOrderRepository.findAll()).thenReturn(List.of(existing));
        when(purchaseOrderRepository.findById(22L)).thenReturn(Optional.of(existing));

        assertEquals(List.of(existing), service.getAllPurchaseOrders());
        assertEquals(existing, service.getPurchaseOrderById(22L));
    }

    @Test
    void deletesExistingPurchaseOrder() {
        PurchaseOrder existing = purchaseOrder(3L, 8L, 4.0, "PENDING");
        existing.setPoId(22L);
        when(purchaseOrderRepository.findById(22L)).thenReturn(Optional.of(existing));

        service.deletePurchaseOrder(22L);

        verify(purchaseOrderRepository).delete(existing);
    }

    @Test
    void doesNotDeleteUnknownPurchaseOrder() {
        when(purchaseOrderRepository.findById(404L)).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.deletePurchaseOrder(404L)
        );

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        verify(purchaseOrderRepository, never()).delete(any(PurchaseOrder.class));
    }

    private SalesOrder order(long id) {
        SalesOrder order = new SalesOrder();
        order.setOrderId(id);
        return order;
    }

    private SalesOrderItem orderItem(long orderId, long productId, int quantity) {
        SalesOrderItem item = new SalesOrderItem();
        item.setOrderId(orderId);
        item.setProductId(productId);
        item.setQuantity(quantity);
        return item;
    }

    private BOM bom(long id, long productId, double quantity) {
        BOM bom = new BOM();
        bom.setBomId(id);
        bom.setProductId(productId);
        bom.setMaterialName("Aluminum");
        bom.setQuantity(quantity);
        bom.setUnit("kg");
        return bom;
    }

    private PurchaseOrder purchaseOrder(long orderId, long bomId, double quantity, String status) {
        PurchaseOrder purchaseOrder = new PurchaseOrder();
        purchaseOrder.setOrderId(orderId);
        purchaseOrder.setBomId(bomId);
        purchaseOrder.setQuantity(quantity);
        purchaseOrder.setStatus(status);
        return purchaseOrder;
    }
}
