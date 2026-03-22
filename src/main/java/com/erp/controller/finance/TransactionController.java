package com.erp.controller.finance;

import com.erp.dto.finance.CreateTransactionDTO;
import com.erp.dto.finance.TransactionResponseDTO;
import com.erp.dto.finance.UpdateTransactionDTO;
import com.erp.dto.finance.UpdateTransactionSourceDTO;
import com.erp.service.finance.TransactionService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/finance/transactions")
public class TransactionController {

    private final TransactionService txService;

    public TransactionController(TransactionService txService) {
        this.txService = txService;
    }

    @GetMapping("/company/{companyId}")
    public List<TransactionResponseDTO> listByCompany(@PathVariable("companyId") Long companyId) {
        return txService.listByCompany(companyId);
    }

    @GetMapping("/{id}")
    public TransactionResponseDTO get(@PathVariable("id") Long id) {
        return txService.get(id);
    }

    @PostMapping
    public TransactionResponseDTO create(@RequestBody CreateTransactionDTO dto) {
        return txService.create(dto);
    }

    @PutMapping("/{id}")
    public TransactionResponseDTO update(
            @PathVariable("id") Long id,
            @RequestBody UpdateTransactionDTO dto) {
        return txService.update(id, dto);
    }

    @PatchMapping("/{id}/source")
    public TransactionResponseDTO updateSource(
            @PathVariable("id") Long id,
            @RequestBody UpdateTransactionSourceDTO dto) {
        return txService.updateSource(id, dto);
    }

    @PostMapping("/{id}/post")
    public TransactionResponseDTO post(@PathVariable("id") Long id, @RequestParam String fiscalYear) {
        return txService.postTransaction(id, fiscalYear);
    }
}
