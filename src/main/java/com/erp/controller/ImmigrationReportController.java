package com.erp.controller;

import com.erp.domain.security.AppAction;
import com.erp.domain.security.AppModule;
import com.erp.dto.immigration.ImmigrationExpiryItemDTO;
import com.erp.service.ImmigrationReportService;
import com.erp.service.security.annotation.RequiresPermission;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/immigration")
@RequiredArgsConstructor
public class ImmigrationReportController {

    private final ImmigrationReportService reportService;

    /**
     * Company-wide list of passports / residence permits that are expired or
     * expiring within {@code withinDays}. Company-wide view → requires VIEW_ALL.
     */
    @RequiresPermission(module = AppModule.IMMIGRATION, action = {AppAction.VIEW_ALL})
    @GetMapping("/expiring")
    public ResponseEntity<List<ImmigrationExpiryItemDTO>> expiring(
            @RequestParam(value = "withinDays", defaultValue = "30") int withinDays
    ) {
        return ResponseEntity.ok(reportService.getExpiring(withinDays));
    }
}
