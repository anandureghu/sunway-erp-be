package com.erp.dto.subscription;

import com.erp.domain.subscription.SubscriptionStatus;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CancelSubscriptionRequest {

    /** CANCELLED or SUSPENDED */
    private SubscriptionStatus status;

    private String notes;
}
