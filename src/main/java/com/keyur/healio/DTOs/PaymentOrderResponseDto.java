package com.keyur.healio.DTOs;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class PaymentOrderResponseDto {
    private String txnId;
    private Integer amount;
    private String key;
    private String productInfo;
    private String firstName;
    private String email;
    private String phone;
    private String hash;
    private String payuUrl;
    private String surl;
    private String furl;
}
