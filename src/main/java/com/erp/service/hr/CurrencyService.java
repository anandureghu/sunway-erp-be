package com.erp.service.hr;

import com.erp.domain.hr.Currency;
import com.erp.repo.hr.CurrencyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CurrencyService {

    private final CurrencyRepository repository;

    public List<Currency> getAll() {
        return repository.findAll();
    }

    public Currency getById(Long id) {
        return repository.findById(id)
                .orElseThrow(() ->
                        new RuntimeException("CountryCurrency not found with id: " + id)
                );
    }

    public Currency getByCurrencyCode(String code) {
        return repository.findByCurrencyCode(code)
                .orElseThrow(() ->
                        new RuntimeException("CountryCurrency not found with currency code: " + code)
                );
    }
}
