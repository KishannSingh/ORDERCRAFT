package com.ordercraft.ordercraft.service;

import com.ordercraft.ordercraft.entity.SalesOrder;
import com.ordercraft.ordercraft.entity.SalesOrderItem;
import com.ordercraft.ordercraft.repository.SalesOrderItemRepository;
import com.ordercraft.ordercraft.repository.SalesOrderRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class SalesOrderService {

    private final SalesOrderRepository salesOrderRepository;
    private final SalesOrderItemRepository salesOrderItemRepository;

    public SalesOrderService(
            SalesOrderRepository salesOrderRepository,
            SalesOrderItemRepository salesOrderItemRepository) {

        this.salesOrderRepository = salesOrderRepository;
        this.salesOrderItemRepository = salesOrderItemRepository;
    }

    public SalesOrder saveOrder(SalesOrder order) {

        if (order.getCustomerId() == null || order.getCustomerId() <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Customer ID is required"
            );
        }

        if (order.getOrderDate() == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Order date is required"
            );
        }

        if (order.getStatus() == null || order.getStatus().isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Order status is required"
            );
        }

        if (order.getTotalAmount() <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Order total amount must be greater than 0"
            );
        }

        if (order.getOrderId() != null) {

            List<SalesOrderItem> items =
                    salesOrderItemRepository.findByOrderId(
                            order.getOrderId()
                    );

            if (!items.isEmpty()) {

                double total = 0;

                for (SalesOrderItem item : items) {
                    total +=
                            item.getQuantity() *
                                    item.getUnitPrice();
                }

                order.setTotalAmount(total);
            }
        }

        return salesOrderRepository.save(order);
    }

    public List<SalesOrder> getAllOrders() {
        return salesOrderRepository.findAll();
    }

    public SalesOrder getOrderById(Long id) {

        return salesOrderRepository.findById(id)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Sales order not found with id: " + id
                        )
                );
    }

    public void deleteOrder(Long id) {

        if (!salesOrderRepository.existsById(id)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Sales order not found with id: " + id
            );
        }

        salesOrderRepository.deleteById(id);
    }
}