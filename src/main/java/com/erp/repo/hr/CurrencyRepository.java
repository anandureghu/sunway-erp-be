package com.erp.repo.hr;

import com.erp.domain.hr.Currency;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CurrencyRepository
        extends JpaRepository<Currency, Long> {

    Optional<Currency> findByCurrencyCode(String currencyCode);
}
