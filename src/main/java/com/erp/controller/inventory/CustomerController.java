package com.erp.controller.inventory;

import com.erp.dto.inventory.CustomerCreateDTO;
import com.erp.dto.inventory.CustomerResponseDTO;
import com.erp.dto.inventory.CustomerUpdateDTO;
import com.erp.service.inventory.CustomerService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/customers")
public class CustomerController {

    private final CustomerService customerService;

    public CustomerController(CustomerService customerService) {
        this.customerService = customerService;
    }

    // ---------------- GET ALL ----------------
    @GetMapping
    public List<CustomerResponseDTO> getAll() {
        return customerService.getAllCustomers();
    }

    // ---------------- GET BY ID ----------------
    @GetMapping("/{id}")
    public ResponseEntity<CustomerResponseDTO> getById(@PathVariable("id") Long id) {
        return ResponseEntity.ok(customerService.getCustomerByIdDTO(id));
    }

    // ---------------- CREATE ----------------
    @PostMapping
    public ResponseEntity<CustomerResponseDTO> create(@RequestBody CustomerCreateDTO dto) {
        return ResponseEntity.ok(customerService.createCustomer(dto));
    }

    // ---------------- UPDATE ----------------
    @PutMapping("/{id}")
    public ResponseEntity<CustomerResponseDTO> update(
            @PathVariable("id") Long id,
            @RequestBody CustomerUpdateDTO dto
    ) {
        return ResponseEntity.ok(customerService.updateCustomer(id, dto));
    }

    // ---------------- DELETE ----------------
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") Long id) {
        customerService.deleteCustomer(id);
        return ResponseEntity.noContent().build();
    }
}
