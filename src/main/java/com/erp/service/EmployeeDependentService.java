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

    // -------------------------
    // Create Dependent
    // -------------------------
    public DependentResponseDTO createDependent(DependentRequestDTO dto) {

        Employee employee = employeeRepository.findById(dto.getEmployeeId())
                .orElseThrow(() -> new RuntimeException("Employee not found"));

        EmployeeDependent dep = EmployeeDependent.builder()
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
                .build();

        dependentRepository.save(dep);
        return toDTO(dep);
    }

    // -------------------------
    // Update Dependent
    // -------------------------
    public DependentResponseDTO updateDependent(Long id, DependentRequestDTO dto) {

        EmployeeDependent dep = dependentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Dependent not found"));

        dep.setFirstName(dto.getFirstName());
        dep.setMiddleName(dto.getMiddleName());
        dep.setLastName(dto.getLastName());
        dep.setDateOfBirth(dto.getDateOfBirth());
        dep.setGender(dto.getGender());
        dep.setNationalId(dto.getNationalId());
        dep.setNationality(dto.getNationality());
        dep.setMaritalStatus(dto.getMaritalStatus());
        dep.setRelationship(dto.getRelationship());

        dependentRepository.save(dep);
        return toDTO(dep);
    }

    // -------------------------
    // Get all dependents for an employee
    // -------------------------
    public List<DependentResponseDTO> getDependentsByEmployee(Long employeeId) {
        return dependentRepository.findByEmployeeId(employeeId)
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    // -------------------------
    // Get one dependent
    // -------------------------
    public DependentResponseDTO getDependentById(Long id) {
        return dependentRepository.findById(id)
                .map(this::toDTO)
                .orElseThrow(() -> new RuntimeException("Dependent not found"));
    }

    // -------------------------
    // Delete dependent
    // -------------------------
    public void deleteDependent(Long id) {
        dependentRepository.deleteById(id);
    }

    // -------------------------
    // Convert Entity → DTO
    // -------------------------
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
                .build();
    }
}
