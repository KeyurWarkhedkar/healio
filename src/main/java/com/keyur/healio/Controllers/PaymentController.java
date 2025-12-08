package com.keyur.healio.Controllers;

import com.keyur.healio.DTOs.PaymentOrderResponseDto;
import com.keyur.healio.Services.PaymentService;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@AllArgsConstructor
@RestController
@RequestMapping(value = "/payment")
public class PaymentController {
    //fields
    private PaymentService paymentService;

    @PostMapping(value = "/createOrder/{appointmentId}")
    public ResponseEntity<PaymentOrderResponseDto> createPaymentOrder(@PathVariable int appointmentId) {
        return new ResponseEntity<>(paymentService.createOrder(appointmentId), HttpStatus.CREATED);
    }

    @GetMapping(value = "/verify/success")
    public ResponseEntity<String> verifyPaymentSuccess(@RequestParam Map<String, String> params) {
        return new ResponseEntity<>(paymentService.verifyPayment(params, true), HttpStatus.OK);
    }

    @GetMapping(value = "/verify/failure")
    public ResponseEntity<String> verifyPaymentFailure(@RequestParam Map<String, String> params) {
        return new ResponseEntity<>(paymentService.verifyPayment(params, false), HttpStatus.CONFLICT);
    }
}
