package com.ordercraft.ordercraft;

import com.ordercraft.ordercraft.dto.MaterialRequirement;
import com.ordercraft.ordercraft.entity.BOM;
import com.ordercraft.ordercraft.repository.BOMRepository;
import com.ordercraft.ordercraft.repository.ProductRepository;
import com.ordercraft.ordercraft.repository.PurchaseOrderRepository;
import com.ordercraft.ordercraft.service.BOMService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BOMServiceTest {

    @Mock BOMRepository bomRepository;
    @Mock ProductRepository productRepository;
    @Mock PurchaseOrderRepository purchaseOrderRepository;

    @InjectMocks BOMService service;

    @Test
    void calculatesRequirementsFromBomQuantityPerUnitAndSalesQuantity() {
        when(bomRepository.findByProductId(14L)).thenReturn(List.of(
                bom(8L, 14L, "Aluminum", 2.5, "kg"),
                bom(9L, 14L, "Fasteners", 4, "pcs")
        ));

        List<MaterialRequirement> requirements = service.calculateMaterial(14L, 3);

        assertEquals(2, requirements.size());
        assertEquals("Aluminum", requirements.get(0).getMaterialName());
        assertEquals(7.5, requirements.get(0).getQuantity());
        assertEquals("kg", requirements.get(0).getUnit());
        assertEquals(12.0, requirements.get(1).getQuantity());
    }

    @Test
    void rejectsNonPositiveOrderQuantityForMaterialCalculation() {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.calculateMaterial(14L, 0)
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verify(bomRepository, never()).findByProductId(14L);
    }

    @Test
    void rejectsBomWithUnknownProduct() {
        when(productRepository.existsById(404L)).thenReturn(false);
        BOM request = bom(null, 404L, "Aluminum", 1, "kg");

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.saveBOM(request)
        );

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        verify(bomRepository, never()).save(request);
    }

    private BOM bom(Long id, Long productId, String materialName, double quantity, String unit) {
        BOM bom = new BOM();
        bom.setBomId(id);
        bom.setProductId(productId);
        bom.setMaterialName(materialName);
        bom.setQuantity(quantity);
        bom.setUnit(unit);
        return bom;
    }
}
