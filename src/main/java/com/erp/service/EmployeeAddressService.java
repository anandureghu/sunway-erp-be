package com.erp.service;

import com.erp.domain.Employee;
import com.erp.domain.EmployeeAddress;
import com.erp.domain.EmployeeContactInfo;
import com.erp.dto.contact.EmployeeAddressRequestDTO;
import com.erp.dto.contact.EmployeeAddressResponseDTO;
import com.erp.dto.contact.EmployeeContactInfoRequestDTO;
import com.erp.dto.contact.EmployeeContactInfoResponseDTO;
import com.erp.repo.EmployeeRepository;
import com.erp.repo.contact.EmployeeAddressRepository;
import com.erp.repo.contact.EmployeeContactInfoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class EmployeeAddressService {

    private final EmployeeRepository employeeRepo;
    private final EmployeeContactInfoRepository contactInfoRepo;
    private final EmployeeAddressRepository addressRepo;

    // ======================================================
    // ADD ADDRESS
    // ======================================================
    public EmployeeAddressResponseDTO addAddress(
            Long employeeId,
            EmployeeAddressRequestDTO dto) {

        Employee employee = employeeRepo.findById(employeeId)
                .orElseThrow(() -> new RuntimeException("Employee not found"));

        EmployeeAddress address = EmployeeAddress.builder()
                .line1(dto.getLine1())
                .line2(dto.getLine2())
                .city(dto.getCity())
                .state(dto.getState())
                .country(dto.getCountry())
                .postalCode(dto.getPostalCode())
                .addressType(dto.getAddressType())
                .primaryAddress(dto.getPrimaryAddress())
                .employee(employee)
                .build();

        return mapToResponse(addressRepo.save(address));
    }

    // ======================================================
    // UPDATE ADDRESS
    // ======================================================
    public EmployeeAddressResponseDTO updateAddress(
            Long addressId,
            EmployeeAddressRequestDTO dto) {

        EmployeeAddress address = addressRepo.findById(addressId)
                .orElseThrow(() -> new RuntimeException("Address not found"));

        address.setLine1(dto.getLine1());
        address.setLine2(dto.getLine2());
        address.setCity(dto.getCity());
        address.setState(dto.getState());
        address.setCountry(dto.getCountry());
        address.setPostalCode(dto.getPostalCode());
        address.setAddressType(dto.getAddressType());
        address.setPrimaryAddress(dto.getPrimaryAddress());

        return mapToResponse(address);
    }

    // ======================================================
    // DELETE ADDRESS
    // ======================================================
    public void deleteAddress(Long addressId) {
        addressRepo.deleteById(addressId);
    }

    // ======================================================
    // GET ALL ADDRESSES (BY EMPLOYEE)
    // ======================================================
    public List<EmployeeAddressResponseDTO> getAddresses(Long employeeId) {

        return addressRepo.findByEmployeeId(employeeId)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    // ======================================================
    // GET CONTACT INFO
    // ======================================================
    public EmployeeContactInfoResponseDTO getContactInfo(Long employeeId) {

        EmployeeContactInfo contactInfo = contactInfoRepo
                .findByEmployeeId(employeeId)
                .orElse(null);

        if (contactInfo == null) {
            return EmployeeContactInfoResponseDTO.builder()
                    .email(null)
                    .phone(null)
                    .altPhone(null)
                    .addresses(List.of())
                    .build();
        }

        return EmployeeContactInfoResponseDTO.builder()
                .email(contactInfo.getEmail())
                .phone(contactInfo.getPhone())
                .altPhone(contactInfo.getAltPhone())
                .addresses(
                        addressRepo.findByEmployeeId(employeeId)
                                .stream()
                                .map(this::mapToResponse)
                                .collect(Collectors.toList())
                )
                .build();
    }

    // ======================================================
    // SAVE / UPDATE CONTACT INFO
    // ======================================================
    public EmployeeContactInfoResponseDTO saveOrUpdateContactInfo(
            Long employeeId,
            EmployeeContactInfoRequestDTO dto) {

        Employee employee = employeeRepo.findById(employeeId)
                .orElseThrow(() -> new RuntimeException("Employee not found"));

        EmployeeContactInfo contactInfo = contactInfoRepo
                .findByEmployeeId(employeeId)
                .orElseGet(() ->
                        EmployeeContactInfo.builder()
                                .employee(employee)
                                .build()
                );

        if (dto.getEmail() == null || dto.getEmail().isBlank()) {
            throw new RuntimeException("Email is required");
        }

        contactInfo.setEmail(dto.getEmail());
        contactInfo.setPhone(dto.getPhone());
        contactInfo.setAltPhone(dto.getAltPhone());

        contactInfoRepo.save(contactInfo);

        return EmployeeContactInfoResponseDTO.builder()
                .email(contactInfo.getEmail())
                .phone(contactInfo.getPhone())
                .altPhone(contactInfo.getAltPhone())
                .addresses(
                        addressRepo.findByEmployeeId(employeeId)
                                .stream()
                                .map(this::mapToResponse)
                                .collect(Collectors.toList())
                )
                .build();
    }

    // ======================================================
    // MAPPER
    // ======================================================
    private EmployeeAddressResponseDTO mapToResponse(EmployeeAddress a) {
        return EmployeeAddressResponseDTO.builder()
                .id(a.getId())
                .line1(a.getLine1())
                .line2(a.getLine2())
                .city(a.getCity())
                .state(a.getState())
                .country(a.getCountry())
                .postalCode(a.getPostalCode())
                .addressType(a.getAddressType())
                .primaryAddress(a.getPrimaryAddress())
                .build();
    }
}
