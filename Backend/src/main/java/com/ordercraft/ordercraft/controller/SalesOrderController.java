package com.ordercraft.ordercraft.controller;

import com.ordercraft.ordercraft.entity.SalesOrder;
import com.ordercraft.ordercraft.service.SalesOrderService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@CrossOrigin(origins = "http://localhost:4200")
@RequestMapping("/orders")
public class SalesOrderController {

    private final SalesOrderService salesOrderService;


    public SalesOrderController(SalesOrderService salesOrderService) {
        this.salesOrderService = salesOrderService;
    }


    @PostMapping
    public SalesOrder addOrder(@RequestBody SalesOrder order) {
        return salesOrderService.saveOrder(order);
    }


    @GetMapping
    public List<SalesOrder> getAllOrders() {
        return salesOrderService.getAllOrders();
    }


    @GetMapping("/{id}")
    public SalesOrder getOrder(@PathVariable Long id) {
        return salesOrderService.getOrderById(id);
    }


    @PutMapping("/{id}")
    public SalesOrder updateOrder(
            @PathVariable Long id,
            @RequestBody SalesOrder order) {

        SalesOrder existingOrder =
                salesOrderService.getOrderById(id);

        existingOrder.setCustomerId(order.getCustomerId());
        existingOrder.setOrderDate(order.getOrderDate());
        existingOrder.setStatus(order.getStatus());
        existingOrder.setTotalAmount(order.getTotalAmount());

        return salesOrderService.saveOrder(existingOrder);
    }


    @DeleteMapping("/{id}")
    public void deleteOrder(@PathVariable Long id) {
        salesOrderService.deleteOrder(id);
    }

}