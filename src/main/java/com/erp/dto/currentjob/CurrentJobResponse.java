// src/main/java/com/hrmodule/dto/currentjob/CurrentJobResponse.java
package com.erp.dto.currentjob;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public class CurrentJobResponse {
    public Long id;
    public Long employeeId;

    public String jobCode;
    public String jobTitle;
    public String jobLevel;
    public String grade;
    public String departmentCode;
    public String departmentName;

    @JsonFormat(pattern = "yyyy-MM-dd")
    public LocalDate effectiveFrom;
    @JsonFormat(pattern = "yyyy-MM-dd")
    public LocalDate startDate;
    @JsonFormat(pattern = "yyyy-MM-dd")
    public LocalDate expectedEndDate;

    public List<CurrentJobRequest.PreviousExperienceDTO> previousExperiences;
    public List<CurrentJobRequest.EducationDTO> educations;

    // → server tells FE exactly what changed
    public Map<String, Object> changedFields;
}
