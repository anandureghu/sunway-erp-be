package com.erp.service;

import com.erp.domain.DocumentSequence;
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

    public DocumentSequenceService(
            DocumentSequenceRepository repo,
            AuthContext authContext,
            EmployeeRepository employeeRepository) {
        this.repo = repo;
        this.authContext = authContext;
        this.employeeRepository = employeeRepository;
    }

    /**
     * Next per-company employee number as a numeric string (e.g. "1000"). Each
     * company has its own sequence keyed {@code {companyId}_EMP}; brand-new
     * companies start at 1000, existing ones continue from their current max.
     *
     * <p>Runs in the caller's transaction and takes a pessimistic row lock, so
     * two concurrent employee creations in the same company can never receive
     * the same number.
     */
    @Transactional
    public String nextEmployeeNo(Long companyId) {
        if (companyId == null) {
            throw new IllegalStateException("Company is required to generate an employee number");
        }
        String key = companyId + "_EMP";
        DocumentSequence seq = repo.findForUpdate(key)
                .orElseGet(() -> new DocumentSequence(key, seedFor(companyId)));

        long value = seq.getNextValue();
        seq.setNextValue(value + 1);
        repo.save(seq);
        return String.valueOf(value);
    }

    /**
     * Create a company's employee-number sequence up front (at 1000) when the
     * company is created, so the very first employee never hits the lazy-seed
     * path. Idempotent — does nothing if the sequence already exists.
     */
    @Transactional
    public void initEmployeeSequence(Long companyId) {
        if (companyId == null) {
            return;
        }
        String key = companyId + "_EMP";
        if (!repo.existsById(key)) {
            repo.save(new DocumentSequence(key, seedFor(companyId)));
        }
    }

    /** First number for a company's sequence: max existing number + 1, but at least 1000. */
    private long seedFor(Long companyId) {
        long max = employeeRepository.findMaxNumericEmployeeNo(companyId);
        return Math.max(1000L, max + 1);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String generateNext(String prefix) {
        Long companyId = authContext.getCurrentCompanyId();
        String sequenceKey = (companyId != null ? companyId + "_" : "") + prefix;

        DocumentSequence seq = repo.findById(sequenceKey).orElse(new DocumentSequence(sequenceKey, 1000L));
        Long val = seq.getNextValue();
        seq.setNextValue(val + 1);
        repo.save(seq);
        return prefix + "-" + val;
    }
}
