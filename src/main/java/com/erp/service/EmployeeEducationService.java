package com.erp.service;

import com.erp.domain.*;
import com.erp.dto.currentjob.EmployeeEducationRequestDTO;
import com.erp.dto.currentjob.EmployeeEducationResponseDTO;
import com.erp.mapper.EmployeeEducationMapper;
import com.erp.repo.EmployeeEducationRepo;
import com.erp.repo.EmployeeRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
@Service
@RequiredArgsConstructor
@Transactional
public class EmployeeEducationService {

    private final EmployeeEducationRepo repo;
    private final EmployeeRepository employeeRepo;

    public List<EmployeeEducationResponseDTO> getAll(Long employeeId) {
        return repo.findByEmployeeId(employeeId)
                .stream()
                .map(EmployeeEducationMapper::toDTO)
                .toList();
    }

    public EmployeeEducationResponseDTO create(Long employeeId, EmployeeEducationRequestDTO dto) {
        Employee employee = employeeRepo.findById(employeeId)
                .orElseThrow(() -> new RuntimeException("Employee not found"));

        EmployeeEducation edu = new EmployeeEducation();
        edu.setEmployee(employee);
        EmployeeEducationMapper.updateEntity(edu, dto);

        repo.save(edu);
        return EmployeeEducationMapper.toDTO(edu);
    }

    public EmployeeEducationResponseDTO update(Long employeeId, Long eduId, EmployeeEducationRequestDTO dto) {
        EmployeeEducation edu = repo.findByIdAndEmployeeId(eduId, employeeId)
                .orElseThrow(() -> new RuntimeException("Education not found"));

        EmployeeEducationMapper.updateEntity(edu, dto);
        return EmployeeEducationMapper.toDTO(edu);
    }

    public void delete(Long employeeId, Long eduId) {
        EmployeeEducation edu = repo.findByIdAndEmployeeId(eduId, employeeId)
                .orElseThrow(() -> new RuntimeException("Education not found"));
        repo.delete(edu);
    }
}
