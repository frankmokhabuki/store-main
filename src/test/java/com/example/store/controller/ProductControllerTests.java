package com.example.store.controller;

import com.example.store.entity.Order;
import com.example.store.entity.Product;
import com.example.store.mapper.CustomerMapper;
import com.example.store.repository.ProductRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ProductController.class)
@ComponentScan(basePackageClasses = CustomerMapper.class)
class ProductControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ProductRepository productRepository;

    private Product product;

    @BeforeEach
    void setUp() {
        Order first = new Order();
        first.setId(5L);
        Order second = new Order();
        second.setId(7L);

        product = new Product();
        product.setId(2L);
        product.setDescription("Wireless Mouse");
        product.setOrders(new LinkedHashSet<>(List.of(first, second)));
    }

    @Test
    void testCreateProduct() throws Exception {
        when(productRepository.save(any(Product.class))).thenReturn(product);

        mockMvc.perform(post("/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(product)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.description").value("Wireless Mouse"));
    }

    @Test
    void testGetAllProducts() throws Exception {
        when(productRepository.findAllWithOrders()).thenReturn(List.of(product));

        mockMvc.perform(get("/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(2))
                .andExpect(jsonPath("$[0].description").value("Wireless Mouse"))
                .andExpect(jsonPath("$[0].orders").isArray())
                .andExpect(jsonPath("$[0].orders.length()").value(2))
                .andExpect(jsonPath("$[0].orders[0]").value(5))
                .andExpect(jsonPath("$[0].orders[1]").value(7));
    }

    @Test
    void testGetProductById() throws Exception {
        when(productRepository.findWithOrdersById(2L)).thenReturn(Optional.of(product));

        mockMvc.perform(get("/products/2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(2))
                .andExpect(jsonPath("$.description").value("Wireless Mouse"))
                .andExpect(jsonPath("$.orders.length()").value(2));
    }

    @Test
    void testGetProductByIdNotFound() throws Exception {
        when(productRepository.findWithOrdersById(999L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/products/999")).andExpect(status().isNotFound());
    }
}
