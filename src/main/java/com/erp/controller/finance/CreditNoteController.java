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

    @RequiresPermission(module = AppModule.FINANCE_INVOICE, action = {AppAction.CREATE})
    @PostMapping
    public CreditNoteResponseDTO createCreditNote(@RequestBody CreateCreditNoteDTO dto) {
        return creditNoteService.createCreditNote(dto);
    }
}
