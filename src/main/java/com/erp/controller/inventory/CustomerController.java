package com.erp.controller.inventory;

import com.erp.domain.security.AppAction;
import com.erp.domain.security.AppModule;
import com.erp.dto.inventory.CustomerCreateDTO;
import com.erp.dto.inventory.CustomerResponseDTO;
import com.erp.dto.inventory.CustomerUpdateDTO;
import com.erp.service.inventory.CustomerService;
import com.erp.service.security.annotation.RequiresPermission;
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

    @RequiresPermission(module = AppModule.INVENTORY_SALES, action = {AppAction.VIEW_ALL, AppAction.VIEW_OWN})
    @GetMapping
    public List<CustomerResponseDTO> getAll() {
        return customerService.getAllCustomers();
    }

    @RequiresPermission(module = AppModule.INVENTORY_SALES, action = {AppAction.VIEW_ALL, AppAction.VIEW_OWN})
    @GetMapping("/{id}")
    public ResponseEntity<CustomerResponseDTO> getById(@PathVariable("id") Long id) {
        return ResponseEntity.ok(customerService.getCustomerByIdDTO(id));
    }

    @RequiresPermission(module = AppModule.INVENTORY_SALES, action = {AppAction.CREATE})
    @PostMapping
    public ResponseEntity<CustomerResponseDTO> create(@RequestBody CustomerCreateDTO dto) {
        return ResponseEntity.ok(customerService.createCustomer(dto));
    }

    @RequiresPermission(module = AppModule.INVENTORY_SALES, action = {AppAction.EDIT})
    @PutMapping("/{id}")
    public ResponseEntity<CustomerResponseDTO> update(
            @PathVariable("id") Long id,
            @RequestBody CustomerUpdateDTO dto
    ) {
        return ResponseEntity.ok(customerService.updateCustomer(id, dto));
    }

    @RequiresPermission(module = AppModule.INVENTORY_SALES, action = {AppAction.DELETE})
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") Long id) {
        customerService.deleteCustomer(id);
        return ResponseEntity.noContent().build();
    }
}
