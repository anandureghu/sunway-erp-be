package com.erp.dto.subscription;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class ExtendSubscriptionRequest {

    @NotNull
    private LocalDate newEndsAt;

    private String notes;
}
