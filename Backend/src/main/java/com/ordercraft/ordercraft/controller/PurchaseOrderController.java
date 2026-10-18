package com.ordercraft.ordercraft.controller;

import com.ordercraft.ordercraft.entity.PurchaseOrder;
import com.ordercraft.ordercraft.service.PurchaseOrderService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@CrossOrigin(origins = "http://localhost:4200")
@RequestMapping("/purchase-orders")
public class PurchaseOrderController {

    private final PurchaseOrderService purchaseOrderService;


    public PurchaseOrderController(
            PurchaseOrderService purchaseOrderService) {
        this.purchaseOrderService = purchaseOrderService;
    }


    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PurchaseOrder addPurchaseOrder(
            @RequestBody PurchaseOrder purchaseOrder) {
        if (purchaseOrder != null) purchaseOrder.setPoId(null);
        return purchaseOrderService
                .savePurchaseOrder(purchaseOrder);
    }


    @GetMapping
    public List<PurchaseOrder> getAllPurchaseOrders() {

        return purchaseOrderService
                .getAllPurchaseOrders();
    }


    @GetMapping("/{id}")
    public PurchaseOrder getPurchaseOrder(
            @PathVariable Long id) {

        return purchaseOrderService
                .getPurchaseOrderById(id);
    }


    @PutMapping("/{id}")
    public PurchaseOrder updatePurchaseOrder(
            @PathVariable Long id,
            @RequestBody PurchaseOrder purchaseOrder) {

        if (purchaseOrder == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Purchase order data is required");
        }

        PurchaseOrder existing =
                purchaseOrderService
                        .getPurchaseOrderById(id);

        existing.setOrderId(purchaseOrder.getOrderId());
        existing.setBomId(purchaseOrder.getBomId());
        existing.setMaterialName(
                purchaseOrder.getMaterialName()
        );
        existing.setQuantity(
                purchaseOrder.getQuantity()
        );
        existing.setStatus(
                purchaseOrder.getStatus()
        );

        return purchaseOrderService
                .savePurchaseOrder(existing);
    }


    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePurchaseOrder(
            @PathVariable Long id) {

        purchaseOrderService
                .deletePurchaseOrder(id);
    }

}
