package com.keyur.healio.DTOs;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@RequiredArgsConstructor
public class PayURefundResponseDto {
    private boolean success;      // true if refund queued/success
    private String mihpayid;      // same id sent
    private String refundId;      // PayU refund ID
    private String requestId;     // PayU request ID
    private String status;        // success/failed
    private String message;
}
