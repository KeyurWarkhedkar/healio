package com.keyur.healio.Services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.keyur.healio.DTOs.PayURefundResponseDto;
import com.keyur.healio.DTOs.PaymentOrderResponseDto;
import com.keyur.healio.Entities.Payment;
import com.keyur.healio.Entities.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

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
        dto.setSurl("https://dorie-lunulate-breezily.ngrok-free.dev/payment/verify/success");
        dto.setFurl("https://dorie-lunulate-breezily.ngrok-free.dev/payment/verify/failure");
        return dto;
    }

    public PaymentOrderResponseDto convertToResponse(Payment payment, User user) {
        return createPaymentResponse(payment, user);
    }

    public String generatePayuHash(String txnId, int amount, User user) {
        String productInfo = "Counselling Appointment";
        String firstName = user.getName();
        String email = user.getEmail();
        String amountStr = String.valueOf(amount); // Or String.format("%.2f", (double) amount)

        String hashString = merchantKey + "|" + txnId + "|" + amountStr + "|" +
                productInfo + "|" + firstName + "|" + email +
                "|||||||||||" + merchantSalt; // 11 pipes as required

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

    //method for processing refunds
    public PayURefundResponseDto refund(String mihpayid, double amount) {

        String command = "refund_payment";
        String hashStr = merchantKey + "|" + command + "|" + mihpayid + "|" + merchantSalt;
        String hash = sha512(hashStr);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("key", merchantKey);
        body.add("command", command);
        body.add("var1", mihpayid);
        body.add("var2", String.valueOf(amount));
        body.add("hash", hash);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);

        RestTemplate restTemplate = new RestTemplate();
        ResponseEntity<String> response =
                restTemplate.postForEntity("https://test.payu.in/merchant/postservice?form=2", request, String.class);

        return parseResponse(response.getBody());
    }

    //method for parsing PayU refund response and returning it the frontend
    private PayURefundResponseDto parseResponse(String responseBody) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(responseBody);

            PayURefundResponseDto res = new PayURefundResponseDto();
            res.setMessage(root.path("msg").asText());

            JsonNode transactionDetails = root.path("transaction_details");

            if (transactionDetails.isMissingNode() || !transactionDetails.fieldNames().hasNext()) {
                res.setSuccess(false);
                res.setStatus("failed");
                return res;
            }

            // The key inside "transaction_details" is the mihpayid value
            String mihpayid = transactionDetails.fieldNames().next();
            JsonNode tx = transactionDetails.get(mihpayid);

            res.setMihpayid(mihpayid);
            res.setRefundId(tx.path("refund_id").asText(null));
            res.setRequestId(tx.path("request_id").asText(null));
            res.setStatus(tx.path("status").asText("failed"));

            res.setSuccess("success".equalsIgnoreCase(res.getStatus()));

            return res;

        } catch (Exception e) {
            PayURefundResponseDto error = new PayURefundResponseDto();
            error.setSuccess(false);
            error.setStatus("error");
            error.setMessage("Failed to parse refund response: " + e.getMessage());
            return error;
        }
    }

}
