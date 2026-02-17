package com.erp.mapper;

import com.erp.domain.EmployeeCurrentJob;
import com.erp.dto.currentjob.*;

public class EmployeeCurrentJobMapper {

    public static EmployeeCurrentJobResponseDTO toDTO(EmployeeCurrentJob e) {
        EmployeeCurrentJobResponseDTO d = new EmployeeCurrentJobResponseDTO();
        d.setId(e.getId());
        d.setEmployeeId(e.getEmployee().getId());
        d.setJobCode(e.getJobCode());
        d.setJobTitle(e.getJobTitle());
        d.setJobLevel(e.getJobLevel());
        d.setGrade(e.getGrade());
        d.setDepartmentCode(e.getDepartmentCode());
        d.setDepartmentName(e.getDepartmentName());
        d.setEffectiveFrom(e.getEffectiveFrom());
        d.setStartDate(e.getStartDate());
        d.setExpectedEndDate(e.getExpectedEndDate());
        d.setWorkLocation(e.getWorkLocation());
        d.setWorkCity(e.getWorkCity());
        d.setWorkCountry(e.getWorkCountry());

        return d;
    }

    public static void updateEntity(EmployeeCurrentJob e, EmployeeCurrentJobRequestDTO d) {
        e.setJobCode(d.getJobCode());
        e.setJobTitle(d.getJobTitle());
        e.setJobLevel(d.getJobLevel());
        e.setGrade(d.getGrade());
        e.setDepartmentCode(d.getDepartmentCode());
        e.setDepartmentName(d.getDepartmentName());
        e.setEffectiveFrom(d.getEffectiveFrom());
        e.setStartDate(d.getStartDate());
        e.setExpectedEndDate(d.getExpectedEndDate());
        e.setWorkLocation(d.getWorkLocation());
        e.setWorkCity(d.getWorkCity());
        e.setWorkCountry(d.getWorkCountry());
    }
}
