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
public class SalesOrderItemService {

    private final SalesOrderItemRepository repository;
    private final SalesOrderRepository salesOrderRepository;


    public SalesOrderItemService(
            SalesOrderItemRepository repository,
            SalesOrderRepository salesOrderRepository) {

        this.repository = repository;
        this.salesOrderRepository = salesOrderRepository;
    }


    public SalesOrderItem saveItem(SalesOrderItem item) {

        if (item.getOrderId() == null || item.getOrderId() <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Order ID is required"
            );
        }

        if (item.getProductId() == null || item.getProductId() <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Product ID is required"
            );
        }

        if (item.getQuantity() <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Quantity must be greater than 0"
            );
        }

        if (item.getUnitPrice() <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Unit price must be greater than 0"
            );
        }


        salesOrderRepository
                .findById(item.getOrderId())
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Sales order not found with id: "
                                        + item.getOrderId()
                        )
                );


        Long oldOrderId = null;


        if (item.getItemId() != null) {

            SalesOrderItem existingItem =
                    repository.findById(item.getItemId())
                            .orElseThrow(() ->
                                    new ResponseStatusException(
                                            HttpStatus.NOT_FOUND,
                                            "Order item not found with id: "
                                                    + item.getItemId()
                                    )
                            );

            oldOrderId = existingItem.getOrderId();
        }


        SalesOrderItem savedItem =
                repository.save(item);


        if (oldOrderId != null &&
                !oldOrderId.equals(item.getOrderId())) {

            recalculateOrderTotal(oldOrderId);
        }


        recalculateOrderTotal(item.getOrderId());


        return savedItem;
    }


    public List<SalesOrderItem> getAllItems() {
        return repository.findAll();
    }


    public SalesOrderItem getItemById(Long id) {

        return repository.findById(id)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Order item not found with id: " + id
                        )
                );
    }


    public void deleteItem(Long id) {

        SalesOrderItem item =
                repository.findById(id)
                        .orElseThrow(() ->
                                new ResponseStatusException(
                                        HttpStatus.NOT_FOUND,
                                        "Order item not found with id: " + id
                                )
                        );


        Long orderId = item.getOrderId();


        repository.deleteById(id);


        if (orderId != null) {
            recalculateOrderTotal(orderId);
        }

    }


    private void recalculateOrderTotal(Long orderId) {

        SalesOrder order =
                salesOrderRepository.findById(orderId)
                        .orElseThrow(() ->
                                new ResponseStatusException(
                                        HttpStatus.NOT_FOUND,
                                        "Sales order not found with id: "
                                                + orderId
                                )
                        );


        List<SalesOrderItem> items =
                repository.findByOrderId(orderId);


        double total = 0;


        for (SalesOrderItem item : items) {

            total +=
                    item.getQuantity() *
                            item.getUnitPrice();

        }


        order.setTotalAmount(total);

        salesOrderRepository.save(order);

    }

}