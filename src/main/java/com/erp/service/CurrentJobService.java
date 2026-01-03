package com.erp.service;

import com.erp.domain.*;
import com.erp.dto.currentjob.*;
import com.erp.mapper.EmployeeCurrentJobMapper;
import com.erp.repo.EmployeeCurrentJobRepo;
import com.erp.repo.EmployeeRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Transactional
public class CurrentJobService {

    private final EmployeeCurrentJobRepo currentJobRepo;
    private final EmployeeRepository employeeRepo;

    public EmployeeCurrentJobResponseDTO get(Long employeeId) {
        return currentJobRepo.findByEmployeeId(employeeId)
                .map(EmployeeCurrentJobMapper::toDTO)
                .orElse(null);
    }

    public EmployeeCurrentJobResponseDTO create(Long employeeId, EmployeeCurrentJobRequestDTO dto) {

        if (currentJobRepo.existsByEmployeeId(employeeId)) {
            throw new RuntimeException("Current job already exists");
        }

        Employee employee = employeeRepo.findById(employeeId)
                .orElseThrow(() -> new RuntimeException("Employee not found"));

        EmployeeCurrentJob job = new EmployeeCurrentJob();
        job.setEmployee(employee);

        EmployeeCurrentJobMapper.updateEntity(job, dto);
        currentJobRepo.save(job);

        return EmployeeCurrentJobMapper.toDTO(job);
    }

    public EmployeeCurrentJobResponseDTO update(Long employeeId, EmployeeCurrentJobRequestDTO dto) {

        EmployeeCurrentJob job = currentJobRepo.findByEmployeeId(employeeId)
                .orElseThrow(() -> new RuntimeException("Current job not found"));

        EmployeeCurrentJobMapper.updateEntity(job, dto);
        return EmployeeCurrentJobMapper.toDTO(job);
    }
}
