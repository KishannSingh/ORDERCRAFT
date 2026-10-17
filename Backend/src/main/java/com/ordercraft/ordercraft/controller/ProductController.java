package com.ordercraft.ordercraft.controller;

import com.ordercraft.ordercraft.entity.Product;
import com.ordercraft.ordercraft.service.ProductService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@CrossOrigin(origins = "http://localhost:4200")
@RequestMapping("/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @PostMapping
    public Product addProduct(
            @RequestBody Product product
    ) {

        return productService.saveProduct(product);
    }

    @GetMapping
    public List<Product> getAllProducts() {

        return productService.getAllProducts();
    }

    @GetMapping("/{id}")
    public Product getProduct(
            @PathVariable Long id
    ) {

        return productService.getProductById(id);
    }

    @PutMapping("/{id}")
    public Product updateProduct(
            @PathVariable Long id,
            @RequestBody Product product
    ) {

        return productService.updateProduct(
                id,
                product
        );
    }

    @DeleteMapping("/{id}")
    public void deleteProduct(
            @PathVariable Long id
    ) {

        productService.deleteProduct(id);
    }
}