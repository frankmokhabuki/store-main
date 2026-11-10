package com.example.store.repository;

import com.example.store.entity.Order;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    /**
     * Fetch all orders together with the owning customer and the products in the order in a single query. Without the
     * fetch joins, DTO mapping triggers one lazy query per order for the customer (and another per order for the
     * products), which is very costly over a high-latency database link.
     */
    @Query("select distinct o from Order o join fetch o.customer left join fetch o.products")
    List<Order> findAllWithDetails();

    @Query("select o from Order o join fetch o.customer left join fetch o.products where o.id = :id")
    Optional<Order> findWithDetailsById(@Param("id") Long id);
}
