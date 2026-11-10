package com.example.store.repository;

import com.example.store.entity.Product;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

    /**
     * Fetch all products together with the orders that contain them in a single query, so the list of order IDs in the
     * DTO does not trigger a lazy query per product.
     */
    @Query("select distinct p from Product p left join fetch p.orders")
    List<Product> findAllWithOrders();

    @Query("select p from Product p left join fetch p.orders where p.id = :id")
    Optional<Product> findWithOrdersById(@Param("id") Long id);
}
