package com.erp.dto.platform;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PlatformSettingsRequest {

    @Size(max = 50)
    private String street;

    @Size(max = 50)
    private String city;

    @Size(max = 50)
    private String state;

    @Size(max = 50)
    private String country;

    @Size(max = 100)
    private String bankName;

    @Size(max = 150)
    private String accountHolder;

    @Size(max = 64)
    private String iban;

    @Size(max = 32)
    private String ifscCode;

    @Size(max = 100)
    private String branchName;
}
