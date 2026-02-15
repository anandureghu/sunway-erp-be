package com.erp.controller.finance;

import com.erp.dto.finance.CreateCreditNoteDTO;
import com.erp.dto.finance.CreditNoteResponseDTO;
import com.erp.service.finance.CreditNoteService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/credit-notes")
@RequiredArgsConstructor
public class CreditNoteController {

    private final CreditNoteService creditNoteService;

    @GetMapping
    public List<CreditNoteResponseDTO> getAll() {
        return creditNoteService.getAllForCompany();
    }

    @PostMapping
    public CreditNoteResponseDTO createCreditNote(@RequestBody CreateCreditNoteDTO dto) {
        return creditNoteService.createCreditNote(dto);
    }
}
