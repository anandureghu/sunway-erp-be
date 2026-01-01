package com.erp.dto.salary;


import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Setter
@Getter
public class PayrollGenerateRequestDTO {

    private LocalDate payPeriodStart;
    private LocalDate payPeriodEnd;
    private LocalDate payDate;

}
