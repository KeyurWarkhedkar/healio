package com.keyur.healio.Services;

import com.keyur.healio.DTOs.PaymentOrderResponseDto;
import com.keyur.healio.Entities.Appointment;

import java.util.Map;

public interface PaymentService {
    public PaymentOrderResponseDto createOrder(int appointmentId);
    public String verifyPayment(Map<String, String> params, boolean success);
}
