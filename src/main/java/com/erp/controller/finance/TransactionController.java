package com.erp.controller.finance;

import com.erp.domain.security.AppAction;
import com.erp.domain.security.AppModule;
import com.erp.dto.common.PageResponse;
import com.erp.dto.finance.CreateTransactionDTO;
import com.erp.dto.finance.TransactionCsvImportResultDTO;
import com.erp.dto.finance.TransactionCsvPreviewDTO;
import com.erp.dto.finance.TransactionResponseDTO;
import com.erp.dto.finance.UpdateTransactionDTO;
import com.erp.dto.finance.UpdateTransactionSourceDTO;
import com.erp.service.finance.TransactionCsvImportService;
import com.erp.service.finance.TransactionService;
import com.erp.service.security.annotation.RequiresPermission;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/finance/transactions")
public class TransactionController {

    private final TransactionService txService;
    private final TransactionCsvImportService csvImportService;

    public TransactionController(TransactionService txService,
                                 TransactionCsvImportService csvImportService) {
        this.txService = txService;
        this.csvImportService = csvImportService;
    }

    @RequiresPermission(module = AppModule.FINANCE_LEDGER, action = {AppAction.VIEW_ALL, AppAction.VIEW_OWN})
    @GetMapping("/company/{companyId}")
    public PageResponse<TransactionResponseDTO> listByCompany(
            @PathVariable("companyId") Long companyId,
            @RequestParam(value = "search", required = false) String search,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return PageResponse.from(txService.listByCompanyPaged(companyId, search, pageable));
    }

    @RequiresPermission(module = AppModule.FINANCE_LEDGER, action = {AppAction.VIEW_ALL, AppAction.VIEW_OWN})
    @GetMapping("/{id}")
    public TransactionResponseDTO get(@PathVariable("id") Long id) {
        return txService.get(id);
    }

    @RequiresPermission(module = AppModule.FINANCE_LEDGER, action = {AppAction.CREATE})
    @PostMapping
    public TransactionResponseDTO create(@RequestBody CreateTransactionDTO dto) {
        return txService.create(dto);
    }

    @RequiresPermission(module = AppModule.FINANCE_LEDGER, action = {AppAction.EDIT})
    @PutMapping("/{id}")
    public TransactionResponseDTO update(
            @PathVariable("id") Long id,
            @RequestBody UpdateTransactionDTO dto) {
        return txService.update(id, dto);
    }

    @RequiresPermission(module = AppModule.FINANCE_LEDGER, action = {AppAction.EDIT})
    @PatchMapping("/{id}/source")
    public TransactionResponseDTO updateSource(
            @PathVariable("id") Long id,
            @RequestBody UpdateTransactionSourceDTO dto) {
        return txService.updateSource(id, dto);
    }

    @RequiresPermission(module = AppModule.FINANCE_LEDGER, action = {AppAction.EDIT})
    @PostMapping("/{id}/archive")
    public TransactionResponseDTO archive(@PathVariable("id") Long id) {
        return txService.archiveTransaction(id);
    }

    @RequiresPermission(module = AppModule.FINANCE_LEDGER, action = {AppAction.APPROVE})
    @PostMapping("/{id}/post")
    public TransactionResponseDTO post(@PathVariable("id") Long id, @RequestParam String fiscalYear) {
        return txService.postTransaction(id, fiscalYear);
    }

    @RequiresPermission(module = AppModule.FINANCE_LEDGER, action = {AppAction.CREATE})
    @PostMapping("/import-csv/preview")
    public TransactionCsvPreviewDTO previewCsv(
            @RequestParam("file") MultipartFile file) {
        return csvImportService.preview(file);
    }

    @RequiresPermission(module = AppModule.FINANCE_LEDGER, action = {AppAction.CREATE})
    @PostMapping("/import-csv")
    public TransactionCsvImportResultDTO importCsv(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "mapping", required = false) String mappingJson) {
        return csvImportService.importCsv(file, mappingJson);
    }
}
