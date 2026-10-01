package com.erp.controller.admin;

import com.erp.dto.admin.PublicEnquiryRequest;
import com.erp.dto.admin.PublicEnquiryResponse;
import com.erp.service.admin.EnquiryService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public/enquiries")
public class PublicEnquiryController {

    private final EnquiryService enquiryService;

    public PublicEnquiryController(EnquiryService enquiryService) {
        this.enquiryService = enquiryService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.OK)
    public PublicEnquiryResponse submit(@Valid @RequestBody PublicEnquiryRequest request) {
        return enquiryService.submitPublic(request);
    }
}
