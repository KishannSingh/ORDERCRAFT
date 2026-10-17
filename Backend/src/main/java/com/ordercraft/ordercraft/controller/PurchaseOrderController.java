package com.ordercraft.ordercraft.controller;

import com.ordercraft.ordercraft.entity.PurchaseOrder;
import com.ordercraft.ordercraft.service.PurchaseOrderService;
import org.springframework.web.bind.annotation.*;

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
    public PurchaseOrder addPurchaseOrder(
            @RequestBody PurchaseOrder purchaseOrder) {

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

        PurchaseOrder existing =
                purchaseOrderService
                        .getPurchaseOrderById(id);

        existing.setOrderId(purchaseOrder.getOrderId());
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
    public void deletePurchaseOrder(
            @PathVariable Long id) {

        purchaseOrderService
                .deletePurchaseOrder(id);
    }

}