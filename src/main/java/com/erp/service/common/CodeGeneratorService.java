package com.erp.service.common;

import com.erp.domain.common.CodeSequence;
import com.erp.repo.hr.CodeSequenceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
@Transactional
public class CodeGeneratorService {

    private final CodeSequenceRepository repository;

    public synchronized String generateContractCode() {

        int year = LocalDate.now().getYear();
        String key = "CONTRACT_" + year;

        CodeSequence sequence = repository.findById(key)
                .orElse(CodeSequence.builder()
                        .codeKey(key)
                        .lastNumber(0L)
                        .build());

        sequence.setLastNumber(sequence.getLastNumber() + 1);
        repository.save(sequence);

        return String.format("CON-%d-%05d", year, sequence.getLastNumber());
    }
}