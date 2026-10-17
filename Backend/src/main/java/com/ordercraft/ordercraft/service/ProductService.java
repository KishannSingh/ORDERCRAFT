package com.ordercraft.ordercraft.service;

import com.ordercraft.ordercraft.entity.Product;
import com.ordercraft.ordercraft.repository.ProductRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class ProductService {

    private final ProductRepository productRepository;

    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    public Product saveProduct(Product product) {

        if (product.getProductCode() == null ||
                product.getProductCode().trim().isEmpty()) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Product code is required"
            );
        }

        if (product.getName() == null ||
                product.getName().trim().isEmpty()) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Product name is required"
            );
        }

        if (product.getPrice() < 0) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Product price cannot be negative"
            );
        }

        product.setProductCode(
                product.getProductCode().trim()
        );

        product.setName(
                product.getName().trim()
        );

        return productRepository.save(product);
    }

    public List<Product> getAllProducts() {
        return productRepository.findAll();
    }

    public Product getProductById(Long id) {

        return productRepository.findById(id)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Product not found with id: " + id
                        )
                );
    }

    public Product updateProduct(
            Long id,
            Product product
    ) {

        Product existingProduct =
                getProductById(id);

        if (product.getProductCode() == null ||
                product.getProductCode().trim().isEmpty()) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Product code is required"
            );
        }

        if (product.getName() == null ||
                product.getName().trim().isEmpty()) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Product name is required"
            );
        }

        if (product.getPrice() < 0) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Product price cannot be negative"
            );
        }

        existingProduct.setProductCode(
                product.getProductCode().trim()
        );

        existingProduct.setName(
                product.getName().trim()
        );

        existingProduct.setPrice(
                product.getPrice()
        );

        return productRepository.save(existingProduct);
    }

    public void deleteProduct(Long id) {

        if (!productRepository.existsById(id)) {

            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Product not found with id: " + id
            );
        }

        productRepository.deleteById(id);
    }
}