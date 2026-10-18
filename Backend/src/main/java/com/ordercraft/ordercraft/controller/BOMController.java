package com.ordercraft.ordercraft.controller;

import com.ordercraft.ordercraft.dto.MaterialRequirement;
import com.ordercraft.ordercraft.entity.BOM;
import com.ordercraft.ordercraft.service.BOMService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@CrossOrigin(origins = "http://localhost:4200")
@RequestMapping("/boms")
public class BOMController {

    private final BOMService bomService;

    public BOMController(BOMService bomService) {
        this.bomService = bomService;
    }

    // =========================
    // ADD BOM
    // =========================
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BOM addBOM(@RequestBody BOM bom) {
        if (bom != null) bom.setBomId(null);
        return bomService.saveBOM(bom);
    }

    // =========================
    // GET ALL BOM
    // =========================
    @GetMapping
    public List<BOM> getAllBOMs() {
        return bomService.getAllBOMs();
    }

    // =========================
    // CALCULATE MATERIAL REQUIREMENT
    // =========================
    @GetMapping("/calculate/{productId}/{orderQuantity}")
    public List<MaterialRequirement> calculateMaterial(
            @PathVariable Long productId,
            @PathVariable int orderQuantity) {

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

        return bomService.calculateMaterial(
                productId,
                orderQuantity
        );
    }

    // =========================
    // GET BOM BY ID
    // =========================
    @GetMapping("/{id}")
    public BOM getBOM(@PathVariable Long id) {
        return bomService.getBOMById(id);
    }

    // =========================
    // UPDATE BOM
    // =========================
    @PutMapping("/{id}")
    public BOM updateBOM(
            @PathVariable Long id,
            @RequestBody BOM bom) {

        if (bom == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "BOM data is required");
        }

        BOM existingBOM =
                bomService.getBOMById(id);

        existingBOM.setProductId(bom.getProductId());
        existingBOM.setMaterialName(bom.getMaterialName());
        existingBOM.setQuantity(bom.getQuantity());
        existingBOM.setUnit(bom.getUnit());

        return bomService.saveBOM(existingBOM);
    }

    // =========================
    // DELETE BOM
    // =========================
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteBOM(@PathVariable Long id) {
        bomService.deleteBOM(id);
    }
}
