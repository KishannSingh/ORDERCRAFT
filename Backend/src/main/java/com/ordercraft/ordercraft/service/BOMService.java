package com.ordercraft.ordercraft.service;

import com.ordercraft.ordercraft.dto.MaterialRequirement;
import com.ordercraft.ordercraft.entity.BOM;
import com.ordercraft.ordercraft.repository.BOMRepository;
import com.ordercraft.ordercraft.repository.PurchaseOrderRepository;
import com.ordercraft.ordercraft.repository.ProductRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@Transactional
public class BOMService {

    private final BOMRepository bomRepository;
    private final ProductRepository productRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;

    public BOMService(
            BOMRepository bomRepository,
            ProductRepository productRepository,
            PurchaseOrderRepository purchaseOrderRepository) {
        this.bomRepository = bomRepository;
        this.productRepository = productRepository;
        this.purchaseOrderRepository = purchaseOrderRepository;
    }

    // =========================
    // ADD / SAVE BOM
    // =========================
    public BOM saveBOM(BOM bom) {

        if (bom == null) {
            throw badRequest("BOM data is required");
        }

        if (bom.getProductId() == null || bom.getProductId() <= 0) {
            throw badRequest("A valid product ID is required");
        }

        if (!productRepository.existsById(bom.getProductId())) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Product not found with id: " + bom.getProductId()
            );
        }

        if (bom.getMaterialName() == null ||
                bom.getMaterialName().isBlank()) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Material name is required"
            );
        }

        if (!Double.isFinite(bom.getQuantity()) || bom.getQuantity() <= 0) {
            throw badRequest("Material quantity must be a finite value greater than 0");
        }

        if (bom.getUnit() == null ||
                bom.getUnit().isBlank()) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Unit is required"
            );
        }

        bom.setMaterialName(bom.getMaterialName().trim());
        bom.setUnit(bom.getUnit().trim());

        if (bom.getBomId() != null && purchaseOrderRepository.existsByBomId(bom.getBomId())) {
            BOM existing = getBOMById(bom.getBomId());
            boolean requirementChanged = !existing.getProductId().equals(bom.getProductId())
                    || !existing.getMaterialName().equalsIgnoreCase(bom.getMaterialName())
                    || Double.compare(existing.getQuantity(), bom.getQuantity()) != 0
                    || !existing.getUnit().equalsIgnoreCase(bom.getUnit());
            if (requirementChanged) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "A BOM material referenced by purchase orders cannot be changed"
                );
            }
        }

        return bomRepository.save(bom);
    }

    // =========================
    // GET ALL BOM
    // =========================
    public List<BOM> getAllBOMs() {
        return bomRepository.findAll();
    }

    // =========================
    // GET BOM BY ID
    // =========================
    public BOM getBOMById(Long id) {

        if (id == null || id <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid BOM ID"
            );
        }

        return bomRepository.findById(id)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "BOM not found with id: " + id
                        )
                );
    }

    // =========================
    // DELETE BOM
    // =========================
    public void deleteBOM(Long id) {

        if (id == null || id <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid BOM ID"
            );
        }

        if (!bomRepository.existsById(id)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "BOM not found with id: " + id
            );
        }

        if (purchaseOrderRepository.existsByBomId(id)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Cannot delete a BOM material referenced by purchase orders"
            );
        }

        bomRepository.deleteById(id);
    }

    // =========================
    // MATERIAL REQUIREMENT CALCULATOR
    // =========================
    public List<MaterialRequirement> calculateMaterial(
            Long productId,
            int orderQuantity) {

        if (productId == null || productId <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Product ID must be greater than 0"
            );
        }

        if (orderQuantity <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Order quantity must be greater than 0"
            );
        }

        List<BOM> boms =
                bomRepository.findByProductId(productId);

        if (boms.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "No BOM found for product: " + productId
            );
        }

        return boms.stream()
                .map(bom -> {
                    double required = bom.getQuantity() * orderQuantity;
                    if (!Double.isFinite(required)) {
                        throw badRequest("Calculated material quantity exceeds the supported amount");
                    }
                    return new MaterialRequirement(
                            bom.getMaterialName(),
                            required,
                            bom.getUnit()
                    );
                })
                .toList();
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
