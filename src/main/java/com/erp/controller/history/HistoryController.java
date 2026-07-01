package com.erp.controller.history;

import com.erp.domain.history.HistoryEntityType;
import com.erp.domain.history.HistoryModule;
import com.erp.dto.history.BulkActionResultDTO;
import com.erp.dto.history.HistoryBulkActionRequest;
import com.erp.dto.history.HistoryDeleteAllRequest;
import com.erp.dto.history.HistoryPageResponse;
import com.erp.service.history.HistoryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/history")
public class HistoryController {

    private final HistoryService historyService;

    public HistoryController(HistoryService historyService) {
        this.historyService = historyService;
    }

    @GetMapping
    public ResponseEntity<HistoryPageResponse> list(
            @RequestParam HistoryModule module,
            @RequestParam HistoryEntityType type,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String search
    ) {
        return ResponseEntity.ok(historyService.list(module, type, page, size, search));
    }

    @PostMapping("/bulk-archive")
    public ResponseEntity<BulkActionResultDTO> bulkArchive(@RequestBody HistoryBulkActionRequest request) {
        return ResponseEntity.ok(historyService.bulkArchive(request.getType(), request.getIds()));
    }

    @PostMapping("/bulk-delete")
    public ResponseEntity<BulkActionResultDTO> bulkDelete(@RequestBody HistoryBulkActionRequest request) {
        return ResponseEntity.ok(historyService.bulkDelete(request.getType(), request.getIds()));
    }

    @PostMapping("/delete-all")
    public ResponseEntity<BulkActionResultDTO> deleteAll(@RequestBody HistoryDeleteAllRequest request) {
        return ResponseEntity.ok(historyService.deleteAll(request.getType(), request.getConfirmToken()));
    }
}
