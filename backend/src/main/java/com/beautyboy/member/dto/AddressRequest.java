package com.beautyboy.member.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record AddressRequest(
        @NotBlank @Size(max = 30) String receiver,
        @NotBlank @Size(max = 20) String phone,
        @NotBlank @Size(max = 10) String zipcode,
        @NotBlank @Size(max = 200) String address1,
        @Size(max = 200) String address2,
        BigDecimal latitude,
        BigDecimal longitude,
        boolean isDefault
) {
}
