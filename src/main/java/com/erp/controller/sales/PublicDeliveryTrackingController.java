package com.erp.controller.sales;

import com.erp.dto.sales.PublicDeliveryTrackingCompanyDTO;
import com.erp.dto.sales.PublicDeliveryTrackingLookupRequest;
import com.erp.dto.sales.PublicDeliveryTrackingLookupResponse;
import com.erp.service.sales.PublicDeliveryTrackingService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public/deliveries")
public class PublicDeliveryTrackingController {

    private final PublicDeliveryTrackingService service;

    public PublicDeliveryTrackingController(PublicDeliveryTrackingService service) {
        this.service = service;
    }

    @GetMapping("/{companyCode}")
    public ResponseEntity<PublicDeliveryTrackingCompanyDTO> getCompanyInfo(
            @PathVariable("companyCode") String companyCode
    ) {
        return ResponseEntity.ok(service.getCompanyInfo(companyCode));
    }

    @PostMapping("/{companyCode}/lookup")
    public ResponseEntity<PublicDeliveryTrackingLookupResponse> lookup(
            @PathVariable("companyCode") String companyCode,
            @RequestBody PublicDeliveryTrackingLookupRequest request
    ) {
        return ResponseEntity.ok(service.lookup(companyCode, request));
    }
}
