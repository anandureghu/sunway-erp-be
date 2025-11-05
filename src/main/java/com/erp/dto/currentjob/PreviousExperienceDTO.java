// src/main/java/com/hrmodule/dto/currentjob/PreviousExperienceDTO.java
package com.erp.dto.currentjob;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDate;

public class PreviousExperienceDTO {
    public String previousCompany;
    public String lastJobTitle;
    @JsonFormat(pattern = "yyyy-MM-dd")
    public LocalDate lastDateWorked;
    public String numberOfYears;
}
