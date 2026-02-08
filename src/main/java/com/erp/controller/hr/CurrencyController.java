package com.erp.controller.hr;

import com.erp.domain.hr.Currency;
import com.erp.service.hr.CurrencyService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/currencies")
@RequiredArgsConstructor
public class CurrencyController {

    private final CurrencyService service;

    // 🔹 GET all
    @GetMapping
    public List<Currency> getAll() {
        return service.getAll();
    }

    // 🔹 GET by ID
    @GetMapping("/{id}")
    public Currency getById(@PathVariable Long id) {
        return service.getById(id);
    }

    // 🔹 GET by currency code
    @GetMapping("/by-currency")
    public Currency getByCurrencyCode(@RequestParam String code) {
        return service.getByCurrencyCode(code);
    }
}

