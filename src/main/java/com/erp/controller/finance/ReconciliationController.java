package com.erp.controller.finance;

import com.erp.domain.security.AppAction;
import com.erp.domain.security.AppModule;
import com.erp.dto.finance.CreateReconciliationRequest;
import com.erp.dto.finance.ReconciliationResponse;
import com.erp.dto.finance.UpdateReconciliationRequest;
import com.erp.service.finance.ReconciliationService;
import com.erp.service.security.annotation.RequiresPermission;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/finance/reconciliations")
@RequiredArgsConstructor
public class ReconciliationController {

    private final ReconciliationService service;

    @RequiresPermission(module = AppModule.FINANCE_RECONCILIATION, action = {AppAction.VIEW_ALL, AppAction.VIEW_OWN})
    @GetMapping
    public Page<ReconciliationResponse> getAll(Pageable pageable) {
        return service.getAll(pageable);
    }

    @RequiresPermission(module = AppModule.FINANCE_RECONCILIATION, action = {AppAction.CREATE})
    @PostMapping
    public ReconciliationResponse create(
            @RequestBody CreateReconciliationRequest request
    ) {
        return service.create(request);
    }

    @RequiresPermission(module = AppModule.FINANCE_RECONCILIATION, action = {AppAction.APPROVE})
    @PutMapping("/{id}/confirm")
    public ReconciliationResponse confirm(
            @PathVariable("id") Long id
    ) {
        return service.confirm(id);
    }

    @RequiresPermission(module = AppModule.FINANCE_RECONCILIATION, action = {AppAction.EDIT})
    @PutMapping("/{id}")
    public ReconciliationResponse edit(
            @PathVariable("id") Long id,
            @RequestBody UpdateReconciliationRequest request
    ) {
        return service.edit(id, request);
    }
}