package com.keyur.healio.Services;

import com.keyur.healio.DTOs.PaymentOrderResponseDto;

import java.util.Map;

public interface PaymentService {
    public PaymentOrderResponseDto createOrder(int appointmentId);
    public String verifyPayment(Map<String, String> params, boolean success);
}
