package com.erp.config;

import com.fasterxml.jackson.datatype.hibernate6.Hibernate6Module;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JacksonConfig {

    @Bean
    public Hibernate6Module hibernate6Module() {
        Hibernate6Module module = new Hibernate6Module();
        // By default this module treats @Transient the same as @JsonIgnore, which silently
        // drops every computed/hydrated field (Company.employeeCount, invoiceHeaderSubtitle,
        // storage usage, Payroll.getWorkingDays(), PurchaseOrderItem.getRemainingQuantity(), ...)
        // from every response. Those fields are meant to be serialized, so disable it.
        module.disable(Hibernate6Module.Feature.USE_TRANSIENT_ANNOTATION);
        return module;
    }
}

