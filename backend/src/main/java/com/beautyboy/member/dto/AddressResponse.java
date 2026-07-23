package com.beautyboy.member.dto;

import com.beautyboy.member.Address;

import java.math.BigDecimal;

public record AddressResponse(
        Long id,
        String receiver,
        String phone,
        String zipcode,
        String address1,
        String address2,
        BigDecimal latitude,
        BigDecimal longitude,
        boolean isDefault
) {

    public static AddressResponse from(Address address) {
        return new AddressResponse(
                address.getId(),
                address.getReceiver(),
                address.getPhone(),
                address.getZipcode(),
                address.getAddress1(),
                address.getAddress2(),
                address.getLatitude(),
                address.getLongitude(),
                address.isDefault()
        );
    }
}
