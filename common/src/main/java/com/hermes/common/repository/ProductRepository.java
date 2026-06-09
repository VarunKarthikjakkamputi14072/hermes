package com.hermes.common.repository;

import com.hermes.common.domain.Product;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, String> {

    /**
     * Fetches a product with a pessimistic write lock so concurrent workers
     * serialise on the row while decrementing stock — this is what prevents
     * overselling when the API is being hammered.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.sku = :sku")
    Optional<Product> findBySkuForUpdate(@Param("sku") String sku);
}
