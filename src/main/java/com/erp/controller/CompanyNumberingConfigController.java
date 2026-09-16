package com.erp.controller;

import com.erp.domain.CompanyNumberingConfig;
import com.erp.dto.CompanyNumberingConfigDTO;
import com.erp.repo.CompanyNumberingConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/companies/{companyId}/numbering-config")
@RequiredArgsConstructor
public class CompanyNumberingConfigController {

    private final CompanyNumberingConfigRepository repo;

    /** All known document types with their defaults — returned even if not yet saved. */
    private static final List<CompanyNumberingConfigDTO> DEFAULTS = List.of(
        new CompanyNumberingConfigDTO("EMP",     "",         1000L),
        new CompanyNumberingConfigDTO("PO",      "PO",       1000L),
        new CompanyNumberingConfigDTO("PR",      "PR",       1000L),
        new CompanyNumberingConfigDTO("INV",     "INV",      1000L),
        new CompanyNumberingConfigDTO("SO",      "SO",       1000L),
        new CompanyNumberingConfigDTO("SR",      "SR",       1000L),
        new CompanyNumberingConfigDTO("SH",      "SH",       1000L),
        new CompanyNumberingConfigDTO("PL",      "PL",       1000L),
        new CompanyNumberingConfigDTO("LV",      "LV",       1000L),
        new CompanyNumberingConfigDTO("PAYROLL", "PAYROLL",  1000L),
        new CompanyNumberingConfigDTO("SUP",     "SUP",      1000L),
        new CompanyNumberingConfigDTO("WH",      "WH",       1000L),
        new CompanyNumberingConfigDTO("TX",      "TX",       1000L)
    );

    @GetMapping
    public ResponseEntity<List<CompanyNumberingConfigDTO>> get(@PathVariable Long companyId) {
        var saved = repo.findByCompanyId(companyId).stream()
                .collect(Collectors.toMap(CompanyNumberingConfig::getDocType, c -> c));

        List<CompanyNumberingConfigDTO> result = DEFAULTS.stream()
                .map(def -> {
                    CompanyNumberingConfig c = saved.get(def.getDocType());
                    if (c == null) return def;
                    return new CompanyNumberingConfigDTO(c.getDocType(), c.getPrefix(), c.getStartNumber());
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(result);
    }

    @PutMapping
    public ResponseEntity<List<CompanyNumberingConfigDTO>> save(
            @PathVariable Long companyId,
            @RequestBody List<CompanyNumberingConfigDTO> configs) {

        for (CompanyNumberingConfigDTO dto : configs) {
            if (dto.getDocType() == null || dto.getDocType().isBlank()) continue;
            CompanyNumberingConfig entity = repo.findByCompanyIdAndDocType(companyId, dto.getDocType())
                    .orElse(new CompanyNumberingConfig());
            entity.setCompanyId(companyId);
            entity.setDocType(dto.getDocType());
            entity.setPrefix(dto.getPrefix() != null ? dto.getPrefix().trim() : "");
            entity.setStartNumber(dto.getStartNumber() != null && dto.getStartNumber() > 0
                    ? dto.getStartNumber() : 1000L);
            repo.save(entity);
        }

        return get(companyId);
    }
}
