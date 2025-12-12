package com.erp.controller.finance;

import com.erp.dto.finance.JournalEntryCreateDTO;
import com.erp.dto.finance.JournalEntryResponseDTO;
import com.erp.service.finance.JournalEntryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/finance/journal-entries")
public class JournalEntryController {

    private final JournalEntryService service;

    public JournalEntryController(JournalEntryService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<JournalEntryResponseDTO> create(@RequestBody JournalEntryCreateDTO dto) {
        return ResponseEntity.ok(service.createJE(dto));
    }

    @PostMapping("/{id}/post")
    public ResponseEntity<JournalEntryResponseDTO> post(@PathVariable("id") Long id) {
        return ResponseEntity.ok(service.postJE(id));
    }

    @PostMapping("/{id}/reverse")
    public ResponseEntity<JournalEntryResponseDTO> reverse(@PathVariable("id") Long id) {
        return ResponseEntity.ok(service.reverseJE(id));
    }

    @GetMapping("/{id}")
    public ResponseEntity<JournalEntryResponseDTO> get(@PathVariable("id") Long id) {
        return ResponseEntity.ok(service.getJE(id));
    }

    @GetMapping
    public List<JournalEntryResponseDTO> list() {
        return service.listForCompany();
    }
}
