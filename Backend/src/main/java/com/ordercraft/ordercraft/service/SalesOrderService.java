
package com.ordercraft.ordercraft.service;

import com.ordercraft.ordercraft.entity.SalesOrder;
import com.ordercraft.ordercraft.entity.SalesOrderItem;
import com.ordercraft.ordercraft.repository.CustomerRepository;
import com.ordercraft.ordercraft.repository.InvoiceRepository;
import com.ordercraft.ordercraft.repository.PurchaseOrderRepository;
import com.ordercraft.ordercraft.repository.SalesOrderItemRepository;
import com.ordercraft.ordercraft.repository.SalesOrderRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.math.BigDecimal;

@Service
@Transactional
public class SalesOrderService {

    private static final Set<String> VALID_STATUSES = Set.of(
            "PENDING",
            "CONFIRMED",
            "PROCESSING",
            "SHIPPED",
            "DELIVERED",
            "COMPLETED",
            "CANCELLED"
    );

    private final SalesOrderRepository salesOrderRepository;
    private final SalesOrderItemRepository salesOrderItemRepository;
    private final CustomerRepository customerRepository;
    private final InvoiceRepository invoiceRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;

    public SalesOrderService(
            SalesOrderRepository salesOrderRepository,
            SalesOrderItemRepository salesOrderItemRepository,
            CustomerRepository customerRepository,
            InvoiceRepository invoiceRepository,
            PurchaseOrderRepository purchaseOrderRepository) {
        this.salesOrderRepository = salesOrderRepository;
        this.salesOrderItemRepository = salesOrderItemRepository;
        this.customerRepository = customerRepository;
        this.invoiceRepository = invoiceRepository;
        this.purchaseOrderRepository = purchaseOrderRepository;
    }

    public SalesOrder saveOrder(SalesOrder order) {
        if (order == null) {
            throw badRequest("Sales order data is required");
        }

        if (order.getCustomerId() == null || order.getCustomerId() <= 0) {
            throw badRequest("Valid customer ID is required");
        }
        if (!customerRepository.existsById(order.getCustomerId())) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Customer not found with id: " + order.getCustomerId()
            );
        }

        if (order.getOrderDate() == null) {
            throw badRequest("Order date is required");
        }

        if (order.getStatus() == null || order.getStatus().isBlank()) {
            throw badRequest("Order status is required");
        }

        String status = order.getStatus()
                .trim()
                .toUpperCase(Locale.ROOT);

        if (!VALID_STATUSES.contains(status)) {
            throw badRequest(
                    "Invalid status. Allowed values: " + VALID_STATUSES
            );
        }

        order.setStatus(status);

        if (order.getOrderId() != null) {
            SalesOrder existing = salesOrderRepository
                    .findById(order.getOrderId())
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND,
                            "Sales order not found with id: " + order.getOrderId()
                    ));

            order.setTotalAmount(calculateTotal(
                    salesOrderItemRepository.findByOrderId(existing.getOrderId())
            ));
        } else {
            order.setOrderId(null);
            order.setTotalAmount(0);
        }

        return salesOrderRepository.save(order);
    }

    public List<SalesOrder> getAllOrders() {
        return salesOrderRepository.findAll();
    }

    public SalesOrder getOrderById(Long id) {
        validateId(id);

        return salesOrderRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Sales order not found with id: " + id
                ));
    }

    public void deleteOrder(Long id) {
        validateId(id);

        SalesOrder order = getOrderById(id);

        List<SalesOrderItem> items =
                salesOrderItemRepository.findByOrderId(id);

        if (!items.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Cannot delete this order because it has order items. "
                            + "Remove its items first."
            );
        }

        if (invoiceRepository.existsByOrderId(id)
                || purchaseOrderRepository.existsByOrderId(id)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Cannot delete this order because purchase orders or invoices reference it"
            );
        }

        salesOrderRepository.delete(order);
    }

    private double calculateTotal(List<SalesOrderItem> items) {
        BigDecimal total = BigDecimal.ZERO;

        for (SalesOrderItem item : items) {
            if (item.getQuantity() <= 0
                    || !Double.isFinite(item.getUnitPrice())
                    || item.getUnitPrice() <= 0) {
                throw badRequest(
                        "Order items must have positive quantity "
                                + "and a valid unit price"
                );
            }

            total = total.add(BigDecimal.valueOf(item.getQuantity())
                    .multiply(BigDecimal.valueOf(item.getUnitPrice())));

            if (!Double.isFinite(total.doubleValue())) {
                throw badRequest("Order total is too large");
            }
        }

        return total.doubleValue();
    }

    private void validateId(Long id) {
        if (id == null || id <= 0) {
            throw badRequest("Valid sales order ID is required");
        }
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                message
        );
    }
}
