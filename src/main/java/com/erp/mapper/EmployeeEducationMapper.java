package com.erp.mapper;

import com.erp.domain.EmployeeEducation;
import com.erp.dto.currentjob.EmployeeEducationRequestDTO;
import com.erp.dto.currentjob.EmployeeEducationResponseDTO;

public class EmployeeEducationMapper {

    public static EmployeeEducationResponseDTO toDTO(EmployeeEducation e) {
        EmployeeEducationResponseDTO dto = new EmployeeEducationResponseDTO();
        dto.setId(e.getId());
        dto.setEmployeeId(e.getEmployee().getId());
        dto.setSchoolName(e.getSchoolName());
        dto.setSchoolAddress(e.getSchoolAddress());
        dto.setDegreeEarned(e.getDegreeEarned());
        dto.setMajor(e.getMajor());
        dto.setYearGraduated(e.getYearGraduated());
        dto.setAwards(e.getAwards());
        dto.setNotes(e.getNotes());
        return dto;
    }

    public static void updateEntity(EmployeeEducation e, EmployeeEducationRequestDTO d) {
        e.setSchoolName(d.getSchoolName());
        e.setSchoolAddress(d.getSchoolAddress());
        e.setDegreeEarned(d.getDegreeEarned());
        e.setMajor(d.getMajor());
        e.setYearGraduated(d.getYearGraduated());
        e.setAwards(d.getAwards());
        e.setNotes(d.getNotes());
    }
}
