package com.erp.service;

import com.erp.domain.DocumentSequence;
import com.erp.repo.DocumentSequenceRepository;
import com.erp.security.context.AuthContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DocumentSequenceService {
    private final DocumentSequenceRepository repo;
    private final AuthContext authContext;

    public DocumentSequenceService(DocumentSequenceRepository repo, AuthContext authContext) {
        this.repo = repo;
        this.authContext = authContext;
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
