
package com.ordercraft.ordercraft.service;

import com.ordercraft.ordercraft.entity.SalesOrder;
import com.ordercraft.ordercraft.entity.SalesOrderItem;
import com.ordercraft.ordercraft.repository.SalesOrderItemRepository;
import com.ordercraft.ordercraft.repository.SalesOrderRepository;
import com.ordercraft.ordercraft.repository.ProductRepository;
import com.ordercraft.ordercraft.repository.InvoiceRepository;
import com.ordercraft.ordercraft.repository.PurchaseOrderRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.math.BigDecimal;
import java.util.stream.Stream;

@Service
@Transactional
public class SalesOrderItemService {

    private final SalesOrderItemRepository repository;
    private final SalesOrderRepository salesOrderRepository;
    private final ProductRepository productRepository;
    private final InvoiceRepository invoiceRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;

    public SalesOrderItemService(
            SalesOrderItemRepository repository,
            SalesOrderRepository salesOrderRepository,
            ProductRepository productRepository,
            InvoiceRepository invoiceRepository,
            PurchaseOrderRepository purchaseOrderRepository) {
        this.repository = repository;
        this.salesOrderRepository = salesOrderRepository;
        this.productRepository = productRepository;
        this.invoiceRepository = invoiceRepository;
        this.purchaseOrderRepository = purchaseOrderRepository;
    }

    public SalesOrderItem saveItem(SalesOrderItem item) {
        if (item == null) {
            throw badRequest("Order item data is required");
        }

        validatePositiveId(item.getOrderId(), "Valid order ID is required");
        validatePositiveId(item.getProductId(), "Valid product ID is required");

        if (item.getQuantity() <= 0) {
            throw badRequest("Quantity must be greater than 0");
        }

        if (!Double.isFinite(item.getUnitPrice())
                || item.getUnitPrice() <= 0) {
            throw badRequest("Unit price must be a finite value greater than 0");
        }

        if (!productRepository.existsById(item.getProductId())) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Product not found with id: " + item.getProductId()
            );
        }

        Long oldOrderId = null;

        if (item.getItemId() != null) {
            SalesOrderItem existingItem = repository.findById(item.getItemId())
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND,
                            "Order item not found with id: " + item.getItemId()
                    ));

            oldOrderId = existingItem.getOrderId();
        }

        lockSalesOrders(oldOrderId, item.getOrderId());

        if (invoiceRepository.existsByOrderId(item.getOrderId())
                || (oldOrderId != null && invoiceRepository.existsByOrderId(oldOrderId))) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Sales order items cannot be changed after an invoice has been created"
            );
        }

        if (hasActivePurchaseOrder(item.getOrderId())
                || (oldOrderId != null && !oldOrderId.equals(item.getOrderId())
                && hasActivePurchaseOrder(oldOrderId))) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Sales order items cannot be changed after procurement has been created"
            );
        }

        SalesOrderItem savedItem = repository.save(item);

        if (oldOrderId != null && !oldOrderId.equals(item.getOrderId())) {
            recalculateOrderTotal(oldOrderId);
        }

        recalculateOrderTotal(item.getOrderId());

        return savedItem;
    }

    @Transactional(readOnly = true)
    public List<SalesOrderItem> getAllItems() {
        return repository.findAll();
    }

    @Transactional(readOnly = true)
    public List<SalesOrderItem> getItemsByOrderId(Long orderId) {
        validatePositiveId(orderId, "Valid sales order ID is required");
        if (!salesOrderRepository.existsById(orderId)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Sales order not found with id: " + orderId
            );
        }
        return repository.findByOrderId(orderId);
    }

    @Transactional(readOnly = true)
    public SalesOrderItem getItemById(Long id) {
        validatePositiveId(id, "Valid order item ID is required");

        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Order item not found with id: " + id
                ));
    }

    public void deleteItem(Long id) {
        validatePositiveId(id, "Valid order item ID is required");

        SalesOrderItem item = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Order item not found with id: " + id
                ));

        Long orderId = item.getOrderId();

        if (orderId != null) {
            lockSalesOrders(orderId);
        }

        if (orderId != null && invoiceRepository.existsByOrderId(orderId)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Sales order items cannot be changed after an invoice has been created"
            );
        }

        if (orderId != null && hasActivePurchaseOrder(orderId)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Sales order items cannot be changed after procurement has been created"
            );
        }

        repository.delete(item);

        if (orderId != null) {
            recalculateOrderTotal(orderId);
        }
    }

    private void recalculateOrderTotal(Long orderId) {
        SalesOrder order = salesOrderRepository.findById(orderId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Sales order not found with id: " + orderId
                ));

        List<SalesOrderItem> items = repository.findByOrderId(orderId);

        BigDecimal total = BigDecimal.ZERO;

        for (SalesOrderItem item : items) {
            if (item.getQuantity() <= 0
                    || !Double.isFinite(item.getUnitPrice())
                    || item.getUnitPrice() <= 0) {
                throw badRequest("Order item has invalid quantity or unit price");
            }

            total = total.add(BigDecimal.valueOf(item.getQuantity())
                    .multiply(BigDecimal.valueOf(item.getUnitPrice())));

            if (!Double.isFinite(total.doubleValue())) {
                throw badRequest("Order total exceeds the supported amount");
            }
        }

        order.setTotalAmount(total.doubleValue());
        salesOrderRepository.save(order);
    }

    private void validatePositiveId(Long id, String message) {
        if (id == null || id <= 0) {
            throw badRequest(message);
        }
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                message
        );
    }

    private boolean hasActivePurchaseOrder(Long orderId) {
        return purchaseOrderRepository.findByOrderId(orderId).stream()
                .anyMatch(purchaseOrder -> !"CANCELLED".equalsIgnoreCase(purchaseOrder.getStatus()));
    }

    private void lockSalesOrders(Long... orderIds) {
        Stream.of(orderIds)
                .filter(orderId -> orderId != null)
                .distinct()
                .sorted()
                .forEach(orderId -> salesOrderRepository.findByIdForUpdate(orderId)
                        .orElseThrow(() -> new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Sales order not found with id: " + orderId
                        )));
    }
}
