package com.ordercraft.ordercraft.service;

import com.ordercraft.ordercraft.entity.PurchaseOrder;
import com.ordercraft.ordercraft.repository.PurchaseOrderRepository;
import com.ordercraft.ordercraft.repository.SalesOrderRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class PurchaseOrderService {

    private final PurchaseOrderRepository purchaseOrderRepository;
    private final SalesOrderRepository salesOrderRepository;


    public PurchaseOrderService(
            PurchaseOrderRepository purchaseOrderRepository,
            SalesOrderRepository salesOrderRepository) {

        this.purchaseOrderRepository = purchaseOrderRepository;
        this.salesOrderRepository = salesOrderRepository;
    }


    public PurchaseOrder savePurchaseOrder(
            PurchaseOrder purchaseOrder) {

        if (purchaseOrder.getOrderId() == null ||
                purchaseOrder.getOrderId() <= 0) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Sales Order ID is required"
            );
        }


        if (!salesOrderRepository.existsById(
                purchaseOrder.getOrderId())) {

            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Sales order not found with id: "
                            + purchaseOrder.getOrderId()
            );
        }


        if (purchaseOrder.getMaterialName() == null ||
                purchaseOrder.getMaterialName().isBlank()) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Material name is required"
            );
        }


        if (purchaseOrder.getQuantity() <= 0) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Quantity must be greater than 0"
            );
        }


        if (purchaseOrder.getStatus() == null ||
                purchaseOrder.getStatus().isBlank()) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Purchase order status is required"
            );
        }


        return purchaseOrderRepository.save(
                purchaseOrder
        );
    }


    public List<PurchaseOrder> getAllPurchaseOrders() {

        return purchaseOrderRepository.findAll();
    }


    public PurchaseOrder getPurchaseOrderById(Long id) {

        return purchaseOrderRepository.findById(id)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Purchase order not found with id: "
                                        + id
                        )
                );
    }


    public void deletePurchaseOrder(Long id) {

        if (!purchaseOrderRepository.existsById(id)) {

            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Purchase order not found with id: "
                            + id
            );
        }


        purchaseOrderRepository.deleteById(id);
    }

}