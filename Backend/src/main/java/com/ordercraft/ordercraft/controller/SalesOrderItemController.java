package com.ordercraft.ordercraft.controller;

import com.ordercraft.ordercraft.entity.SalesOrderItem;
import com.ordercraft.ordercraft.service.SalesOrderItemService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@CrossOrigin(origins = "http://localhost:4200")
@RequestMapping("/order-items")
public class SalesOrderItemController {

    private final SalesOrderItemService service;


    public SalesOrderItemController(SalesOrderItemService service) {
        this.service = service;
    }


    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SalesOrderItem addItem(@RequestBody SalesOrderItem item) {
        if (item != null) item.setItemId(null);
        return service.saveItem(item);
    }


    @GetMapping
    public List<SalesOrderItem> getAllItems() {
        return service.getAllItems();
    }

    @GetMapping("/order/{orderId}")
    public List<SalesOrderItem> getItemsByOrder(
            @PathVariable Long orderId
    ) {
        return service.getItemsByOrderId(orderId);
    }


    @GetMapping("/{id}")
    public SalesOrderItem getItem(@PathVariable Long id) {
        return service.getItemById(id);
    }


    @PutMapping("/{id}")
    public SalesOrderItem updateItem(
            @PathVariable Long id,
            @RequestBody SalesOrderItem item) {

        if (item == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Order item data is required");
        }

        SalesOrderItem existingItem =
                service.getItemById(id);

        existingItem.setOrderId(item.getOrderId());
        existingItem.setProductId(item.getProductId());
        existingItem.setQuantity(item.getQuantity());
        existingItem.setUnitPrice(item.getUnitPrice());

        return service.saveItem(existingItem);
    }


    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteItem(@PathVariable Long id) {
        service.deleteItem(id);
    }

}
