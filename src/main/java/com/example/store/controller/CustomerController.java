package com.example.store.controller;

import com.example.store.dto.CustomerDTO;
import com.example.store.entity.Customer;
import com.example.store.mapper.CustomerMapper;
import com.example.store.repository.CustomerRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/customer")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerRepository customerRepository;
    private final CustomerMapper customerMapper;

    /**
     * Returns all customers, or only those whose name contains the given query string (case-insensitive). A substring
     * of any word in the name is by definition also a substring of the full name, so a LIKE match on the full name
     * satisfies the word-substring requirement; the search is delegated to the database so customers that do not match
     * are never loaded over the high-latency link.
     */
    @GetMapping
    public List<CustomerDTO> getCustomers(@RequestParam(required = false) String query) {
        List<Customer> customers = (query == null || query.isBlank())
                ? customerRepository.findAllWithOrders()
                : customerRepository.searchByName(escapeLikeWildcards(query.trim()));
        return customerMapper.customersToCustomerDTOs(customers);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CustomerDTO createCustomer(@RequestBody Customer customer) {
        // The ID is database generated; ignore any value supplied by the client so a POST
        // can never silently update an existing row.
        customer.setId(null);
        return customerMapper.customerToCustomerDTO(customerRepository.save(customer));
    }

    /** Escape LIKE special characters so user input is matched literally. */
    private static String escapeLikeWildcards(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
