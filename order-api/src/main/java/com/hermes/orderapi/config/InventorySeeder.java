package com.hermes.orderapi.config;

import com.hermes.common.domain.Product;
import com.hermes.common.repository.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Seeds the products table from a bundled CSV on first start so the system is
 * demo-ready out of the box. To run against the real Olist catalogue, load it
 * with {@code scripts/load_olist.py} instead (see README) — this seeder only
 * runs when the table is empty.
 */
@Component
public class InventorySeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(InventorySeeder.class);

    private final ProductRepository productRepository;

    public InventorySeeder(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (productRepository.count() > 0) {
            log.info("Products already present ({}), skipping seed", productRepository.count());
            return;
        }

        List<Product> products = new ArrayList<>();
        ClassPathResource resource = new ClassPathResource("seed/products.csv");
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            String line = reader.readLine(); // header
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                String[] cols = line.split(",", -1);
                products.add(new Product(
                        cols[0].trim(),
                        cols[1].trim(),
                        cols[2].trim(),
                        new BigDecimal(cols[3].trim()),
                        Integer.parseInt(cols[4].trim())
                ));
            }
        }
        productRepository.saveAll(products);
        log.info("Seeded {} products into inventory", products.size());
    }
}
