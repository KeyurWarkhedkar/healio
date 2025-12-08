package com.keyur.healio.Services;

import com.keyur.healio.DTOs.PaymentOrderResponseDto;
import com.keyur.healio.Entities.Payment;
import com.keyur.healio.Entities.User;

import java.util.Map;

public interface PayUService {
    public PaymentOrderResponseDto createPaymentResponse(Payment payment, User user);
    public PaymentOrderResponseDto convertToResponse(Payment payment, User user);
    public String generatePayuHash(String txnId, int amount, User user);
    public String sha512(String input);
    public String generatePayuResponseHash(Map<String, String> params);
}
