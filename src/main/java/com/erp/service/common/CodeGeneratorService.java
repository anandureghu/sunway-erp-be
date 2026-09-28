package com.erp.service.common;

import com.erp.service.DocumentSequenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Thin wrappers around {@link DocumentSequenceService} for HR document codes
 * that are configured under Company → Number Sequences.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class CodeGeneratorService {

    /** Doc type key in company_numbering_configs / Number Sequences UI. */
    public static final String CONTRACT_DOC_TYPE = "CTR";

    private final DocumentSequenceService documentSequenceService;

    public String generateContractCode() {
        return documentSequenceService.generateNext(CONTRACT_DOC_TYPE);
    }
}
