package com.erp.dto.admin;

import com.erp.domain.admin.EnquiryChannel;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PublicEnquiryRequest {
    @NotBlank
    @Size(max = 150)
    private String name;

    @NotBlank
    @Email
    @Size(max = 255)
    private String email;

    @Size(max = 40)
    private String phone;

    @Size(max = 100)
    private String interest;

    @Size(max = 4000)
    private String message;

    @NotNull
    private EnquiryChannel channel;
}
