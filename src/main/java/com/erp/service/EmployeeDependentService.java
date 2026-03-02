package com.erp.service;

import com.erp.domain.Employee;
import com.erp.domain.EmployeeDependent;
import com.erp.dto.dependent.DependentRequestDTO;
import com.erp.dto.dependent.DependentResponseDTO;
import com.erp.repo.EmployeeDependentRepository;
import com.erp.repo.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EmployeeDependentService {

    private final EmployeeDependentRepository dependentRepository;
    private final EmployeeRepository employeeRepository;

    // =========================
    // CREATE DEPENDENT
    // =========================
    public DependentResponseDTO createDependent(Long employeeId, DependentRequestDTO dto) {

        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new RuntimeException("Employee not found"));

        EmployeeDependent dependent = EmployeeDependent.builder()
                .employee(employee)
                .firstName(dto.getFirstName())
                .middleName(dto.getMiddleName())
                .lastName(dto.getLastName())
                .dateOfBirth(dto.getDateOfBirth())
                .gender(dto.getGender())
                .nationalId(dto.getNationalId())
                .nationality(dto.getNationality())
                .maritalStatus(dto.getMaritalStatus())
                .relationship(dto.getRelationship())

                // ✅ Contact Information
                .phoneNumber(dto.getPhoneNumber())
                .addressLine1(dto.getAddressLine1())
                .addressLine2(dto.getAddressLine2())
                .city(dto.getCity())
                .state(dto.getState())
                .postalCode(dto.getPostalCode())
                .country(dto.getCountry())

                .build();

        dependentRepository.save(dependent);
        return toDTO(dependent);
    }

    // =========================
    // UPDATE DEPENDENT
    // =========================
    public DependentResponseDTO updateDependent(Long dependentId, DependentRequestDTO dto) {

        EmployeeDependent dependent = dependentRepository.findById(dependentId)
                .orElseThrow(() -> new RuntimeException("Dependent not found"));

        dependent.setFirstName(dto.getFirstName());
        dependent.setMiddleName(dto.getMiddleName());
        dependent.setLastName(dto.getLastName());
        dependent.setDateOfBirth(dto.getDateOfBirth());
        dependent.setGender(dto.getGender());
        dependent.setNationalId(dto.getNationalId());
        dependent.setNationality(dto.getNationality());
        dependent.setMaritalStatus(dto.getMaritalStatus());
        dependent.setRelationship(dto.getRelationship());

        // ✅ Contact Information
        dependent.setPhoneNumber(dto.getPhoneNumber());
        dependent.setAddressLine1(dto.getAddressLine1());
        dependent.setAddressLine2(dto.getAddressLine2());
        dependent.setCity(dto.getCity());
        dependent.setState(dto.getState());
        dependent.setPostalCode(dto.getPostalCode());
        dependent.setCountry(dto.getCountry());

        dependentRepository.save(dependent);
        return toDTO(dependent);
    }

    // =========================
    // GET ALL DEPENDENTS BY EMPLOYEE
    // =========================
    public List<DependentResponseDTO> getDependentsByEmployee(Long employeeId) {
        return dependentRepository.findByEmployeeId(employeeId)
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    // =========================
    // GET SINGLE DEPENDENT
    // =========================
    public DependentResponseDTO getDependentById(Long dependentId) {
        return dependentRepository.findById(dependentId)
                .map(this::toDTO)
                .orElseThrow(() -> new RuntimeException("Dependent not found"));
    }

    // =========================
    // DELETE DEPENDENT
    // =========================
    public void deleteDependent(Long dependentId) {
        if (!dependentRepository.existsById(dependentId)) {
            throw new RuntimeException("Dependent not found");
        }
        dependentRepository.deleteById(dependentId);
    }

    // =========================
    // ENTITY → DTO
    // =========================
    private DependentResponseDTO toDTO(EmployeeDependent dep) {
        return DependentResponseDTO.builder()
                .id(dep.getId())
                .employeeId(dep.getEmployee().getId())
                .firstName(dep.getFirstName())
                .middleName(dep.getMiddleName())
                .lastName(dep.getLastName())
                .dateOfBirth(dep.getDateOfBirth())
                .gender(dep.getGender())
                .nationalId(dep.getNationalId())
                .nationality(dep.getNationality())
                .maritalStatus(dep.getMaritalStatus())
                .relationship(dep.getRelationship())

                // ✅ Contact Information
                .phoneNumber(dep.getPhoneNumber())
                .addressLine1(dep.getAddressLine1())
                .addressLine2(dep.getAddressLine2())
                .city(dep.getCity())
                .state(dep.getState())
                .postalCode(dep.getPostalCode())
                .country(dep.getCountry())

                .build();
    }
}