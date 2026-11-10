package com.example.store.repository;

import com.example.store.entity.Customer;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

    /**
     * Fetch all customers together with their orders in a single query. This avoids the N+1 select problem that arises
     * when the DTO mapping touches the lazily loaded orders collection of each customer - with a high-latency database
     * link each of those extra round trips is expensive.
     */
    @Query("select distinct c from Customer c left join fetch c.orders")
    List<Customer> findAllWithOrders();

    /**
     * Case-insensitive substring search over the customer name. The pattern must already be escaped for LIKE special
     * characters by the caller. Orders are fetch-joined for the same N+1 reason as above.
     */
    @Query("select distinct c from Customer c left join fetch c.orders "
            + "where lower(c.name) like lower(concat('%', :query, '%')) escape '\\'")
    List<Customer> searchByName(@Param("query") String query);
}
