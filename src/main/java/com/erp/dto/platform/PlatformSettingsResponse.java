package com.erp.dto.platform;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlatformSettingsResponse {

    private Long id;
    private String street;
    private String city;
    private String state;
    private String country;
    private String bankName;
    private String iban;
}
