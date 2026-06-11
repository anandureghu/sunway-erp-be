package com.erp.dto.immigration;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * One row in the company-wide immigration expiry report — a passport or
 * residence permit that is expired or expiring within the requested window.
 */
@Getter
@Setter
@Builder
public class ImmigrationExpiryItemDTO {

    private String documentType; // PASSPORT | RESIDENCE_PERMIT

    private Long employeeId;
    private String employeeCode;
    private String employeeName;

    private String documentNumber;
    private LocalDate expiryDate;
    private long daysRemaining; // negative when already expired
    private String status;      // EXPIRED | EXPIRING_SOON
}
