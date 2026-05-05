package com.erp.dto.salary;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Setter
@Getter
public class PayrollGenerateRequestDTO {

    @NotNull
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate payPeriodStart;

    @NotNull
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate payPeriodEnd;

    @NotNull
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate payDate;
}