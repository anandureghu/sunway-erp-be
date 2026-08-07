package com.erp.controller.finance;

import com.erp.domain.security.AppAction;
import com.erp.domain.security.AppModule;
import com.erp.dto.finance.CreateCreditNoteDTO;
import com.erp.dto.finance.CreditNoteResponseDTO;
import com.erp.service.finance.CreditNoteService;
import com.erp.service.security.annotation.RequiresPermission;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/credit-notes")
@RequiredArgsConstructor
public class CreditNoteController {

    private final CreditNoteService creditNoteService;

    @RequiresPermission(module = AppModule.FINANCE_INVOICE, action = {AppAction.VIEW_ALL, AppAction.VIEW_OWN})
    @GetMapping
    public List<CreditNoteResponseDTO> getAll() {
        return creditNoteService.getAllForCompany();
    }

    /** Standing (unapplied) credit notes for a customer or supplier, usable on a future payment. */
    @RequiresPermission(module = AppModule.FINANCE_INVOICE, action = {AppAction.VIEW_ALL, AppAction.VIEW_OWN})
    @GetMapping("/available")
    public List<CreditNoteResponseDTO> getAvailable(
            @RequestParam(value = "customerId", required = false) Long customerId,
            @RequestParam(value = "supplierId", required = false) Long supplierId) {
        if (customerId != null) {
            return creditNoteService.getAvailableForCustomer(customerId);
        }
        if (supplierId != null) {
            return creditNoteService.getAvailableForSupplier(supplierId);
        }
        return List.of();
    }

    @RequiresPermission(module = AppModule.FINANCE_INVOICE, action = {AppAction.CREATE})
    @PostMapping
    public CreditNoteResponseDTO createCreditNote(@RequestBody CreateCreditNoteDTO dto) {
        return creditNoteService.createCreditNote(dto);
    }

    /** Cash out remaining standing credit (customer or supplier) at any time. */
    @RequiresPermission(module = AppModule.FINANCE_INVOICE, action = {AppAction.EDIT, AppAction.CREATE})
    @PostMapping("/{id}/cash-out")
    public CreditNoteResponseDTO cashOut(@PathVariable("id") Long id) {
        return creditNoteService.cashOut(id);
    }
}
