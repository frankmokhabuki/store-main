package com.example.store.controller;

import com.example.store.entity.Customer;
import com.example.store.entity.Order;
import com.example.store.mapper.CustomerMapper;
import com.example.store.repository.CustomerRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CustomerController.class)
@ComponentScan(basePackageClasses = CustomerMapper.class)
class CustomerControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private CustomerRepository customerRepository;

    private Customer customer;

    @BeforeEach
    void setUp() {
        Order order = new Order();
        order.setId(10L);
        order.setDescription("Test Order");

        customer = new Customer();
        customer.setName("John Doe");
        customer.setId(1L);
        customer.setOrders(List.of(order));
        order.setCustomer(customer);
    }

    @Test
    void testCreateCustomer() throws Exception {
        when(customerRepository.save(any(Customer.class))).thenReturn(customer);

        mockMvc.perform(post("/customer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"John Doe\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("John Doe"));
    }

    @Test
    void testGetAllCustomers() throws Exception {
        when(customerRepository.findAllWithOrders()).thenReturn(List.of(customer));

        mockMvc.perform(get("/customer"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("John Doe"))
                .andExpect(jsonPath("$[0].orders[0].description").value("Test Order"));

        verify(customerRepository, never()).searchByName(any());
    }

    @Test
    void testSearchCustomersByQuery() throws Exception {
        when(customerRepository.searchByName(eq("doe"))).thenReturn(List.of(customer));

        mockMvc.perform(get("/customer").param("query", "doe"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("John Doe"));

        verify(customerRepository, never()).findAllWithOrders();
    }

    @Test
    void testSearchCustomersEscapesLikeWildcards() throws Exception {
        when(customerRepository.searchByName(eq("100\\%"))).thenReturn(List.of());

        mockMvc.perform(get("/customer").param("query", "100%"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());

        verify(customerRepository).searchByName("100\\%");
    }

    @Test
    void testSearchCustomersWithBlankQueryReturnsAll() throws Exception {
        when(customerRepository.findAllWithOrders()).thenReturn(List.of(customer));

        mockMvc.perform(get("/customer").param("query", "  "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("John Doe"));

        verify(customerRepository, never()).searchByName(any());
    }
}
