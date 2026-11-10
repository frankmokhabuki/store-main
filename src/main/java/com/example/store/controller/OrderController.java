package com.example.store.controller;

import com.example.store.dto.OrderDTO;
import com.example.store.entity.Customer;
import com.example.store.entity.Order;
import com.example.store.entity.Product;
import com.example.store.mapper.OrderMapper;
import com.example.store.repository.CustomerRepository;
import com.example.store.repository.OrderRepository;
import com.example.store.repository.ProductRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/order")
@RequiredArgsConstructor
public class OrderController {

    private final OrderRepository orderRepository;
    private final CustomerRepository customerRepository;
    private final ProductRepository productRepository;
    private final OrderMapper orderMapper;

    @GetMapping
    public List<OrderDTO> getAllOrders() {
        return orderMapper.ordersToOrderDTOs(orderRepository.findAllWithDetails());
    }

    @GetMapping("/{id}")
    public OrderDTO getOrderById(@PathVariable Long id) {
        return orderRepository
                .findWithDetailsById(id)
                .map(orderMapper::orderToOrderDTO)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found: " + id));
    }

    /**
     * Creates an order. The request body carries the order description plus the ID of the owning customer and,
     * optionally, the IDs of the products in the order, e.g. {@code {"description": "...", "customer": {"id": 1},
     * "products": [{"id": 2}, {"id": 5}]}}. The associations are resolved against the database so an order can never be
     * attached to a customer or product that does not exist.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderDTO createOrder(@RequestBody Order order) {
        order.setId(null); // ID is database generated; never let a POST update an existing row

        if (order.getCustomer() == null || order.getCustomer().getId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "An order requires a customer with an id");
        }
        Long customerId = order.getCustomer().getId();
        Customer customer = customerRepository
                .findById(customerId)
                .orElseThrow(
                        () -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Customer not found: " + customerId));
        order.setCustomer(customer);

        Set<Product> products = new LinkedHashSet<>();
        if (order.getProducts() != null) {
            for (Product requested : order.getProducts()) {
                if (requested.getId() == null) {
                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST, "Each product in an order requires an id");
                }
                Product product = productRepository
                        .findById(requested.getId())
                        .orElseThrow(() -> new ResponseStatusException(
                                HttpStatus.BAD_REQUEST, "Product not found: " + requested.getId()));
                products.add(product);
            }
        }
        order.setProducts(products);

        return orderMapper.orderToOrderDTO(orderRepository.save(order));
    }
}
