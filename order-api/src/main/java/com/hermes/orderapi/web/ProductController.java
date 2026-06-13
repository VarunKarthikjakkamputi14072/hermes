package com.hermes.orderapi.web;

import com.hermes.common.repository.ProductRepository;
import com.hermes.orderapi.web.dto.ProductResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Read-only catalogue endpoint backing the storefront. Stock levels are the
 * source of truth the dashboard polls to show "X remaining" as workers
 * decrement inventory.
 */
@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductRepository productRepository;

    public ProductController(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @GetMapping
    public List<ProductResponse> list() {
        return productRepository.findAll().stream()
                .map(ProductResponse::from)
                .toList();
    }
}
