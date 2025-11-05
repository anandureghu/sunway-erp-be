// src/main/java/com/hrmodule/dto/currentjob/CurrentJobRequest.java
package com.erp.dto.currentjob;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDate;
import java.util.List;

public class CurrentJobRequest {
    // Scalars (nullable for PATCH-like behavior)
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

    // Lists — send only when you want to replace them
    public List<PreviousExperienceDTO> previousExperiences;
    public List<EducationDTO> educations;

    public static class PreviousExperienceDTO {
        public String previousCompany;
        public String lastJobTitle;
        @JsonFormat(pattern = "yyyy-MM-dd")
        public LocalDate lastDateWorked;
        public String numberOfYears;
    }

    public static class EducationDTO {
        public String schoolName;
        public String yearGraduated;
        public String degreeEarned;
        public String awardsCertificates;
        public String major;
        public String notes;
    }
}
