package com.erp.exception;

import lombok.Getter;

import java.util.List;

@Getter
public class PayrollExportException extends RuntimeException {

    private final List<String> details;

    public PayrollExportException(String message) {
        super(message);
        this.details = List.of();
    }

    public PayrollExportException(String message, List<String> details) {
        super(message);
        this.details = details != null ? details : List.of();
    }
}
