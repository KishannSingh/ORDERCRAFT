package com.ordercraft.ordercraft.service;

import com.ordercraft.ordercraft.entity.PurchaseOrder;
import com.ordercraft.ordercraft.entity.BOM;
import com.ordercraft.ordercraft.entity.SalesOrderItem;
import com.ordercraft.ordercraft.repository.BOMRepository;
import com.ordercraft.ordercraft.repository.PurchaseOrderRepository;
import com.ordercraft.ordercraft.repository.SalesOrderRepository;
import com.ordercraft.ordercraft.repository.SalesOrderItemRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.math.BigDecimal;
import java.util.stream.Stream;

@Service
@Transactional
public class PurchaseOrderService {

    private static final Set<String> VALID_STATUSES = Set.of(
            "PENDING", "ORDERED", "RECEIVED", "CANCELLED"
    );

    private final PurchaseOrderRepository purchaseOrderRepository;
    private final SalesOrderRepository salesOrderRepository;
    private final SalesOrderItemRepository salesOrderItemRepository;
    private final BOMRepository bomRepository;


    public PurchaseOrderService(
            PurchaseOrderRepository purchaseOrderRepository,
            SalesOrderRepository salesOrderRepository,
            SalesOrderItemRepository salesOrderItemRepository,
            BOMRepository bomRepository) {

        this.purchaseOrderRepository = purchaseOrderRepository;
        this.salesOrderRepository = salesOrderRepository;
        this.salesOrderItemRepository = salesOrderItemRepository;
        this.bomRepository = bomRepository;
    }


    public PurchaseOrder savePurchaseOrder(
            PurchaseOrder purchaseOrder) {

        if (purchaseOrder == null) {
            throw badRequest("Purchase order data is required");
        }

        PurchaseOrder existingPurchaseOrder = null;
        if (purchaseOrder.getPoId() != null) {
            validateId(purchaseOrder.getPoId());
            existingPurchaseOrder = purchaseOrderRepository.findByIdForUpdate(purchaseOrder.getPoId())
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND,
                            "Purchase order not found with id: " + purchaseOrder.getPoId()
                    ));
        }

        if (purchaseOrder.getOrderId() == null ||
                purchaseOrder.getOrderId() <= 0) {

            throw badRequest("A valid sales order ID is required");
        }


        List<Long> affectedOrderIds = Stream.of(
                        purchaseOrder.getOrderId(),
                        existingPurchaseOrder == null ? null : existingPurchaseOrder.getOrderId())
                .filter(id -> id != null)
                .distinct()
                .sorted()
                .toList();
        for (Long affectedOrderId : affectedOrderIds) {
            salesOrderRepository.findByIdForUpdate(affectedOrderId)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND,
                            "Sales order not found with id: " + affectedOrderId
                    ));
        }

        List<SalesOrderItem> orderItems = salesOrderItemRepository.findByOrderId(purchaseOrder.getOrderId());
        if (orderItems.isEmpty()) {
            throw badRequest("Add at least one product to the sales order before creating procurement");
        }

        Long bomId = purchaseOrder.getBomId();
        if (bomId == null || bomId <= 0) {
            throw badRequest("A valid BOM material reference is required");
        }

        BOM bom = bomRepository.findById(bomId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "BOM material not found with id: " + bomId
                ));

        BigDecimal productQuantity = orderItems.stream()
                .filter(item -> bom.getProductId().equals(item.getProductId()))
                .map(SalesOrderItem::getQuantity)
                .map(BigDecimal::valueOf)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (productQuantity.signum() <= 0) {
            throw badRequest("The selected BOM material does not belong to a product on this sales order");
        }

        BigDecimal requiredQuantity = productQuantity.multiply(BigDecimal.valueOf(bom.getQuantity()));
        if (requiredQuantity.signum() <= 0 || !Double.isFinite(requiredQuantity.doubleValue())) {
            throw badRequest("Required material quantity is invalid or exceeds the supported amount");
        }

        String suppliedMaterialName = purchaseOrder.getMaterialName();
        if (suppliedMaterialName != null && !suppliedMaterialName.isBlank()
                && !suppliedMaterialName.trim().equalsIgnoreCase(bom.getMaterialName())) {
            throw badRequest("Material name does not match the selected BOM material");
        }
        purchaseOrder.setMaterialName(bom.getMaterialName());


        if (!Double.isFinite(purchaseOrder.getQuantity())
                || purchaseOrder.getQuantity() <= 0) {
            throw badRequest("Quantity must be a finite value greater than 0");
        }

        BigDecimal alreadyProcured = purchaseOrderRepository.findByOrderId(purchaseOrder.getOrderId()).stream()
                .filter(existing -> bomId.equals(existing.getBomId()))
                .filter(existing -> purchaseOrder.getPoId() == null
                        || !purchaseOrder.getPoId().equals(existing.getPoId()))
                .filter(existing -> !"CANCELLED".equalsIgnoreCase(existing.getStatus()))
                .map(PurchaseOrder::getQuantity)
                .map(BigDecimal::valueOf)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal requestedQuantity = BigDecimal.valueOf(purchaseOrder.getQuantity());
        boolean consumesDemand = !"CANCELLED".equalsIgnoreCase(purchaseOrder.getStatus());
        BigDecimal totalProcured = consumesDemand ? alreadyProcured.add(requestedQuantity) : alreadyProcured;
        if (requestedQuantity.compareTo(requiredQuantity) > 0 || totalProcured.compareTo(requiredQuantity) > 0) {
            BigDecimal remaining = requiredQuantity.subtract(alreadyProcured).max(BigDecimal.ZERO);
            throw badRequest("Purchase quantity exceeds the remaining BOM requirement of " + remaining.toPlainString());
        }


        if (purchaseOrder.getStatus() == null ||
                purchaseOrder.getStatus().isBlank()) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Purchase order status is required"
            );
        }

        String status = purchaseOrder.getStatus().trim().toUpperCase(Locale.ROOT);
        if (!VALID_STATUSES.contains(status)) {
            throw badRequest("Invalid purchase order status. Allowed values: " + VALID_STATUSES);
        }
        purchaseOrder.setStatus(status);
        purchaseOrder.setBomId(bomId);


        return purchaseOrderRepository.save(
                purchaseOrder
        );
    }


    @Transactional(readOnly = true)
    public List<PurchaseOrder> getAllPurchaseOrders() {

        return purchaseOrderRepository.findAll();
    }


    @Transactional(readOnly = true)
    public PurchaseOrder getPurchaseOrderById(Long id) {

        validateId(id);

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

        PurchaseOrder purchaseOrder = getPurchaseOrderById(id);

        purchaseOrderRepository.delete(purchaseOrder);
    }

    private void validateId(Long id) {
        if (id == null || id <= 0) {
            throw badRequest("A valid purchase order ID is required");
        }
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

}
