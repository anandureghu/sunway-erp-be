package com.erp.controller.finance;

import com.erp.domain.security.AppAction;
import com.erp.domain.security.AppModule;
import com.erp.dto.finance.CreateJournalEntryRequest;
import com.erp.dto.finance.JournalEntryResponse;
import com.erp.dto.finance.UpdateJournalEntryRequest;
import com.erp.service.finance.JournalEntryService;
import com.erp.service.security.annotation.RequiresPermission;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/finance/journal-entries")
@RequiredArgsConstructor
public class JournalEntryController {

    private final JournalEntryService service;


    @RequiresPermission(module = AppModule.FINANCE_JOURNAL, action = {AppAction.VIEW_ALL, AppAction.VIEW_OWN})
    @GetMapping
    public Page<JournalEntryResponse> getAll(Pageable pageable) {
        return service.getAll(pageable);
    }

    // ============================
    // CREATE
    // ============================
    @RequiresPermission(module = AppModule.FINANCE_JOURNAL, action = {AppAction.CREATE})
    @PostMapping
    public JournalEntryResponse create(
            @RequestBody CreateJournalEntryRequest request
    ) {
        return service.create(request);
    }

    // ============================
    // APPROVE
    // ============================
    @RequiresPermission(module = AppModule.FINANCE_JOURNAL, action = {AppAction.APPROVE})
    @PutMapping("/{id}/approve")
    public JournalEntryResponse approve(
            @PathVariable("id") Long id
    ) {
        return service.approve(id);
    }

    // ============================
    // REJECT
    // ============================
    @RequiresPermission(module = AppModule.FINANCE_JOURNAL, action = {AppAction.APPROVE})
    @PutMapping("/{id}/reject")
    public JournalEntryResponse reject(
            @PathVariable("id") Long id
    ) {
        return service.reject(id);
    }

    // ============================
    // HOLD
    // ============================
    @RequiresPermission(module = AppModule.FINANCE_JOURNAL, action = {AppAction.APPROVE})
    @PutMapping("/{id}/hold")
    public JournalEntryResponse hold(
            @PathVariable("id") Long id
    ) {
        return service.hold(id);
    }

    @RequiresPermission(module = AppModule.FINANCE_JOURNAL, action = {AppAction.EDIT})
    @PutMapping("/{id}")
    public JournalEntryResponse edit(
            @PathVariable("id") Long id,
            @RequestBody UpdateJournalEntryRequest request
    ) {
        return service.edit(id, request);
    }
}