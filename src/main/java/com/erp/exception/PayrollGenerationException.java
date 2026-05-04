package com.erp.exception;

import lombok.Getter;

import java.util.List;

@Getter
public class PayrollGenerationException extends RuntimeException {

    private final List<String> details;

    public PayrollGenerationException(String message) {
        super(message);
        this.details = List.of();
    }

    public PayrollGenerationException(String message, List<String> details) {
        super(message);
        this.details = details != null ? details : List.of();
    }
}
