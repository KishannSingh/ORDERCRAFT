package com.ordercraft.ordercraft.service;

import com.ordercraft.ordercraft.entity.Product;
import com.ordercraft.ordercraft.repository.BOMRepository;
import com.ordercraft.ordercraft.repository.ProductRepository;
import com.ordercraft.ordercraft.repository.SalesOrderItemRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@Transactional
public class ProductService {

    private final ProductRepository productRepository;
    private final BOMRepository bomRepository;
    private final SalesOrderItemRepository salesOrderItemRepository;

    public ProductService(
            ProductRepository productRepository,
            BOMRepository bomRepository,
            SalesOrderItemRepository salesOrderItemRepository
    ) {
        this.productRepository = productRepository;
        this.bomRepository = bomRepository;
        this.salesOrderItemRepository = salesOrderItemRepository;
    }

    public Product saveProduct(Product product) {
        validateProduct(product);
        if (product.getProductId() != null) {
            throw badRequest("Product ID must not be supplied when creating a product");
        }
        String code = product.getProductCode().trim();
        if (productRepository.existsByProductCodeIgnoreCase(code)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A product with this code already exists");
        }
        product.setProductCode(code);
        product.setName(product.getName().trim());
        return productRepository.save(product);
    }

    @Transactional(readOnly = true)
    public List<Product> getAllProducts() {
        return productRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Product getProductById(Long id) {
        validateId(id);
        return productRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Product not found with id: " + id
                ));
    }

    public Product updateProduct(Long id, Product product) {
        Product existing = getProductById(id);
        validateProduct(product);
        String code = product.getProductCode().trim();
        if (productRepository.existsByProductCodeIgnoreCaseAndProductIdNot(code, id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A product with this code already exists");
        }
        existing.setProductCode(code);
        existing.setName(product.getName().trim());
        existing.setPrice(product.getPrice());
        return productRepository.save(existing);
    }

    public void deleteProduct(Long id) {
        Product product = getProductById(id);
        if (bomRepository.existsByProductId(id)
                || salesOrderItemRepository.existsByProductId(id)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Cannot delete this product because BOM or sales order records reference it"
            );
        }
        productRepository.delete(product);
    }

    private void validateProduct(Product product) {
        if (product == null) {
            throw badRequest("Product data is required");
        }
        if (product.getProductCode() == null || product.getProductCode().isBlank()) {
            throw badRequest("Product code is required");
        }
        if (product.getName() == null || product.getName().isBlank()) {
            throw badRequest("Product name is required");
        }
        if (!Double.isFinite(product.getPrice()) || product.getPrice() < 0) {
            throw badRequest("Product price must be a finite non-negative value");
        }
    }

    private void validateId(Long id) {
        if (id == null || id <= 0) {
            throw badRequest("A valid product ID is required");
        }
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
