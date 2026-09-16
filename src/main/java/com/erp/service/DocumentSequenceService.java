package com.erp.service;

import com.erp.domain.CompanyNumberingConfig;
import com.erp.domain.DocumentSequence;
import com.erp.repo.CompanyNumberingConfigRepository;
import com.erp.repo.DocumentSequenceRepository;
import com.erp.repo.EmployeeRepository;
import com.erp.security.context.AuthContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DocumentSequenceService {

    private final DocumentSequenceRepository repo;
    private final AuthContext authContext;
    private final EmployeeRepository employeeRepository;
    private final CompanyNumberingConfigRepository numberingConfigRepo;

    public DocumentSequenceService(
            DocumentSequenceRepository repo,
            AuthContext authContext,
            EmployeeRepository employeeRepository,
            CompanyNumberingConfigRepository numberingConfigRepo) {
        this.repo = repo;
        this.authContext = authContext;
        this.employeeRepository = employeeRepository;
        this.numberingConfigRepo = numberingConfigRepo;
    }

    /**
     * Next per-company employee number. Format is determined by the company's
     * numbering config for doc type "EMP":
     *  - If prefix is blank: plain numeric string (e.g. "1000")
     *  - If prefix is set: "EMP-1000"
     * Start value defaults to 1000 but is configurable.
     */
    @Transactional
    public String nextEmployeeNo(Long companyId) {
        if (companyId == null) {
            throw new IllegalStateException("Company is required to generate an employee number");
        }
        CompanyNumberingConfig cfg = numberingConfigRepo
                .findByCompanyIdAndDocType(companyId, "EMP").orElse(null);
        String configuredPrefix = cfg != null && cfg.getPrefix() != null ? cfg.getPrefix().trim() : "";
        long startNumber = cfg != null && cfg.getStartNumber() != null ? cfg.getStartNumber() : 1000L;

        String key = companyId + "_EMP";
        DocumentSequence seq = repo.findForUpdate(key)
                .orElseGet(() -> new DocumentSequence(key, seedFor(companyId, startNumber)));

        long value = seq.getNextValue();
        seq.setNextValue(value + 1);
        repo.save(seq);

        return configuredPrefix.isBlank() ? String.valueOf(value) : configuredPrefix + "-" + value;
    }

    /**
     * Create a company's employee-number sequence up front when the company is
     * created. Idempotent — does nothing if the sequence already exists.
     */
    @Transactional
    public void initEmployeeSequence(Long companyId) {
        if (companyId == null) return;
        String key = companyId + "_EMP";
        if (!repo.existsById(key)) {
            CompanyNumberingConfig cfg = numberingConfigRepo
                    .findByCompanyIdAndDocType(companyId, "EMP").orElse(null);
            long startNumber = cfg != null && cfg.getStartNumber() != null ? cfg.getStartNumber() : 1000L;
            repo.save(new DocumentSequence(key, seedFor(companyId, startNumber)));
        }
    }

    /** First number: max existing + 1, but at least the configured start. */
    private long seedFor(Long companyId, long startNumber) {
        long max = employeeRepository.findMaxNumericEmployeeNo(companyId);
        return Math.max(startNumber, max + 1);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String generateNext(String docType) {
        Long companyId = authContext.getCurrentCompanyId();
        return generateNext(companyId, docType);
    }

    /**
     * Company-scoped sequence. The {@code docType} is the internal key (e.g. "PO",
     * "INV"). The output prefix and start number come from the company's numbering
     * config for that docType; defaults are used if no config is saved yet.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String generateNext(Long companyId, String docType) {
        String sequenceKey = (companyId != null ? companyId + "_" : "") + docType;

        // Resolve prefix and start number from config, falling back to docType itself
        String outputPrefix = docType;
        long startNumber = 1000L;
        if (companyId != null) {
            CompanyNumberingConfig cfg = numberingConfigRepo
                    .findByCompanyIdAndDocType(companyId, docType).orElse(null);
            if (cfg != null) {
                if (cfg.getPrefix() != null && !cfg.getPrefix().isBlank()) {
                    outputPrefix = cfg.getPrefix().trim();
                }
                if (cfg.getStartNumber() != null) {
                    startNumber = cfg.getStartNumber();
                }
            }
        }

        DocumentSequence seq = repo.findById(sequenceKey)
                .orElse(new DocumentSequence(sequenceKey, startNumber));
        Long val = seq.getNextValue();
        seq.setNextValue(val + 1);
        repo.save(seq);
        return outputPrefix + "-" + val;
    }
}
