package com.erp.service.inventory;

import com.erp.domain.hr.Company;
import com.erp.domain.inventory.Customer;
import com.erp.dto.inventory.CustomerCreateDTO;
import com.erp.dto.inventory.CustomerResponseDTO;
import com.erp.dto.inventory.CustomerUpdateDTO;
import com.erp.repo.inventory.CustomerRepository;
import com.erp.security.context.AuthContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class CustomerService {

    private final CustomerRepository customerRepo;
    private final AuthContext authContext;

    public CustomerService(CustomerRepository customerRepo, AuthContext authContext) {
        this.customerRepo = customerRepo;
        this.authContext = authContext;
    }

    // ---------------- LIST ----------------
    public List<CustomerResponseDTO> getAllCustomers() {
        Long companyId = authContext.getCurrentCompanyId();
        return customerRepo.findByCompanyId(companyId)
                .stream()
                .map(this::toDTO)
                .toList();
    }

    // ---------------- GET BY ID ----------------
    public CustomerResponseDTO getCustomerByIdDTO(Long id) {
        Customer c = getCustomerById(id);

        if (!c.getCompany().getId().equals(authContext.getCurrentCompanyId())) {
            throw new RuntimeException("Access denied");
        }

        return toDTO(c);
    }

    private Customer getCustomerById(Long id) {
        return customerRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Customer not found"));
    }

    // ---------------- CREATE ----------------
    public CustomerResponseDTO createCustomer(CustomerCreateDTO dto) {
        Long companyId = authContext.getCurrentCompanyId();

        Customer c = Customer.builder()
                .customerName(dto.getCustomerName())
                .taxId(dto.getTaxId())
                .paymentTerms(dto.getPaymentTerms())
                .currencyCode(dto.getCurrencyCode())
                .creditLimit(dto.getCreditLimit())
                .street(dto.getStreet())
                .city(dto.getCity())
                .state(dto.getState())
                .country(dto.getCountry())
                .phoneNo(dto.getPhoneNo())
                .email(dto.getEmail())
                .contactPersonName(dto.getContactPersonName())
                .websiteUrl(dto.getWebsiteUrl())
                .customerType(dto.getCustomerType())
                .company(Company.builder().id(companyId).build())
                .build();

        return toDTO(customerRepo.save(c));
    }

    // ---------------- UPDATE ----------------
    public CustomerResponseDTO updateCustomer(Long id, CustomerUpdateDTO dto) {
        Customer existing = getCustomerById(id);

        if (!existing.getCompany().getId().equals(authContext.getCurrentCompanyId())) {
            throw new RuntimeException("Access denied");
        }

        if (dto.getCustomerName() != null) existing.setCustomerName(dto.getCustomerName());
        if (dto.getTaxId() != null) existing.setTaxId(dto.getTaxId());
        if (dto.getPaymentTerms() != null) existing.setPaymentTerms(dto.getPaymentTerms());
        if (dto.getCurrencyCode() != null) existing.setCurrencyCode(dto.getCurrencyCode());
        if (dto.getCreditLimit() != null) existing.setCreditLimit(dto.getCreditLimit());
        if (dto.getIsActive() != null) existing.setActive(dto.getIsActive());

        if (dto.getStreet() != null) existing.setStreet(dto.getStreet());
        if (dto.getCity() != null) existing.setCity(dto.getCity());
        if (dto.getState() != null) existing.setState(dto.getState());
        if (dto.getCountry() != null) existing.setCountry(dto.getCountry());
        if (dto.getPhoneNo() != null) existing.setPhoneNo(dto.getPhoneNo());
        if (dto.getEmail() != null) existing.setEmail(dto.getEmail());

        if (dto.getContactPersonName() != null) existing.setContactPersonName(dto.getContactPersonName());
        if (dto.getWebsiteUrl() != null) existing.setWebsiteUrl(dto.getWebsiteUrl());
        if (dto.getCustomerType() != null) existing.setCustomerType(dto.getCustomerType());

        return toDTO(customerRepo.save(existing));
    }

    // ---------------- DELETE ----------------
    public void deleteCustomer(Long id) {
        Customer existing = getCustomerById(id);
        if (!existing.getCompany().getId().equals(authContext.getCurrentCompanyId())) {
            throw new RuntimeException("Access denied");
        }
        customerRepo.delete(existing);
    }

    // ---------------- MAPPER ----------------
    public CustomerResponseDTO toDTO(Customer c) {
        return CustomerResponseDTO.builder()
                .id(c.getId())
                .customerName(c.getCustomerName())
                .taxId(c.getTaxId())
                .paymentTerms(c.getPaymentTerms())
                .currencyCode(c.getCurrencyCode())
                .creditLimit(c.getCreditLimit())
                .isActive(c.isActive())
                .street(c.getStreet())
                .city(c.getCity())
                .state(c.getState())
                .country(c.getCountry())
                .phoneNo(c.getPhoneNo())
                .email(c.getEmail())
                .contactPersonName(c.getContactPersonName())
                .websiteUrl(c.getWebsiteUrl())
                .customerType(c.getCustomerType())
                .companyId(c.getCompany().getId())
                .build();
    }
}
