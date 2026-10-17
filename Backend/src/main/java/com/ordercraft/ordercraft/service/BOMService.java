package com.ordercraft.ordercraft.service;

import com.ordercraft.ordercraft.dto.MaterialRequirement;
import com.ordercraft.ordercraft.entity.BOM;
import com.ordercraft.ordercraft.repository.BOMRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class BOMService {

    private final BOMRepository bomRepository;

    public BOMService(BOMRepository bomRepository) {
        this.bomRepository = bomRepository;
    }

    // =========================
    // ADD / SAVE BOM
    // =========================
    public BOM saveBOM(BOM bom) {

        if (bom.getProductId() == null || bom.getProductId() <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Product ID is required"
            );
        }

        if (bom.getMaterialName() == null ||
                bom.getMaterialName().isBlank()) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Material name is required"
            );
        }

        if (bom.getQuantity() <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Material quantity must be greater than 0"
            );
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
                .map(bom -> new MaterialRequirement(
                        bom.getMaterialName(),
                        bom.getQuantity() * orderQuantity,
                        bom.getUnit()
                ))
                .toList();
    }
}