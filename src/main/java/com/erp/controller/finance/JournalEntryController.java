package com.erp.controller.finance;

import com.erp.dto.finance.CreateJournalEntryRequest;
import com.erp.dto.finance.JournalEntryResponse;
import com.erp.dto.finance.UpdateJournalEntryRequest;
import com.erp.service.finance.JournalEntryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/finance/journal-entries")
@RequiredArgsConstructor
public class JournalEntryController {

    private final JournalEntryService service;


    @GetMapping
    public Page<JournalEntryResponse> getAll(Pageable pageable) {
        return service.getAll(pageable);
    }

    // ============================
    // CREATE
    // ============================
    @PostMapping
    public JournalEntryResponse create(
            @RequestBody CreateJournalEntryRequest request
    ) {
        return service.create(request);
    }

    // ============================
    // APPROVE
    // ============================
    @PutMapping("/{id}/approve")
    public JournalEntryResponse approve(
            @PathVariable("id") Long id
    ) {
        return service.approve(id);
    }

    // ============================
    // REJECT
    // ============================
    @PutMapping("/{id}/reject")
    public JournalEntryResponse reject(
            @PathVariable("id") Long id
    ) {
        return service.reject(id);
    }

    // ============================
    // HOLD
    // ============================
    @PutMapping("/{id}/hold")
    public JournalEntryResponse hold(
            @PathVariable("id") Long id
    ) {
        return service.hold(id);
    }

    @PutMapping("/{id}")
    public JournalEntryResponse edit(
            @PathVariable("id") Long id,
            @RequestBody UpdateJournalEntryRequest request
    ) {
        return service.edit(id, request);
    }
}