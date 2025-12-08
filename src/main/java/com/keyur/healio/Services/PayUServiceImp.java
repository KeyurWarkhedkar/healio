package com.keyur.healio.Services;

import com.keyur.healio.DTOs.PaymentOrderResponseDto;
import com.keyur.healio.Entities.Payment;
import com.keyur.healio.Entities.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;

@Service
public class PayUServiceImp implements PayUService {

    @Value("${payu.merchant.key}")
    private String merchantKey;

    @Value("${payu.merchant.salt}")
    private String merchantSalt;

    @Value("${payu.base.url}")
    private String payuBaseUrl;

    public PaymentOrderResponseDto createPaymentResponse(Payment payment, User user) {
        String hash = generatePayuHash(payment.getGatewayOrderId(), (int) payment.getAmount(), user);

        PaymentOrderResponseDto dto = new PaymentOrderResponseDto();
        dto.setTxnId(payment.getGatewayOrderId());
        dto.setAmount((int) payment.getAmount());
        dto.setKey(merchantKey);
        dto.setProductInfo("Counselling Appointment");
        dto.setFirstName(user.getName());
        dto.setEmail(user.getEmail());
        dto.setPhone("");
        dto.setHash(hash);
        dto.setPayuUrl(payuBaseUrl);
        dto.setSurl("https://yourapp.com/payment/verify/success");
        dto.setFurl("https://yourapp.com/payment/verify/failure");
        return dto;
    }

    public PaymentOrderResponseDto convertToResponse(Payment payment, User user) {
        return createPaymentResponse(payment, user);
    }

    public String generatePayuHash(String txnId, int amount, User user) {
        String productInfo = "Counselling Appointment";
        String firstName = user.getName();
        String email = user.getEmail();

        String hashString = merchantKey + "|" + txnId + "|" + amount + "|" +
                productInfo + "|" + firstName + "|" + email + "|||||||||||" +
                merchantSalt;

        return sha512(hashString);
    }

    public String sha512(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-512");
            byte[] messageDigest = md.digest(input.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : messageDigest) {
                sb.append(String.format("%02x", b & 0xff));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-512 algorithm not available", e);
        }
    }

    public String generatePayuResponseHash(Map<String, String> params) {
        String key = params.get("key");
        String txnid = params.get("txnid");
        String amount = params.get("amount");
        String productInfo = params.get("productinfo");
        String firstName = params.get("firstname");
        String email = params.get("email");
        String status = params.get("status");

        // Construct string in reverse order for verification
        String hashString = merchantSalt + "|" + status + "|||||||||||" + email + "|" + firstName + "|" +
                productInfo + "|" + amount + "|" + txnid + "|" + key;

        return sha512(hashString);
    }

}
