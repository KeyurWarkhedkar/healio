package com.keyur.healio.ServiceTests;

import com.keyur.healio.Entities.Appointment;
import com.keyur.healio.Entities.Payment;
import com.keyur.healio.Entities.User;
import com.keyur.healio.Enums.AppointmentStatus;
import com.keyur.healio.Enums.PaymentStatus;
import com.keyur.healio.Repositories.AppointmentRepository;
import com.keyur.healio.Repositories.PaymentRepository;
import com.keyur.healio.Services.AppointmentEventPublisher;
import com.keyur.healio.Services.PayUServiceImp;
import com.keyur.healio.Services.PaymentServiceImp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @InjectMocks
    private PaymentServiceImp paymentService;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private AppointmentRepository appointmentRepository;

    @Mock
    private PayUServiceImp payUServiceImp;

    @Mock
    private AppointmentEventPublisher publisher;

    private Payment payment;
    private Appointment appointment;

    @BeforeEach
    void setup() {
        User counsellor = new User();
        User student = new User();
        counsellor.setEmail("counsellor@example.com");
        student.setEmail("student@example.com");

        appointment = new Appointment();
        appointment.setId(1);
        appointment.setAppointmentStatus(AppointmentStatus.PENDING_PAYMENT);
        appointment.setCounsellor(counsellor);
        appointment.setStudent(student);

        payment = new Payment();
        payment.setPaymentStatus(PaymentStatus.PENDING);
        payment.setAppointment(appointment);
        payment.setGatewayOrderId("txn123");
        payment.setAmount(500);
    }

    @Test
    void shouldThrowWhenPaymentNotFound() {
        when(paymentRepository.findByGatewayOrderIdWithLock("txn123")).thenReturn(Optional.empty());

        Map<String, String> params = Map.of("txnid", "txn123");
        assertThrows(RuntimeException.class, () -> paymentService.verifyPayment(params));
    }

    @Test
    void shouldFailWhenHashMismatch() {
        when(paymentRepository.findByGatewayOrderIdWithLock("txn123")).thenReturn(Optional.of(payment));
        when(payUServiceImp.generatePayuResponseHash(any())).thenReturn("wrongHash");

        Map<String, String> params = Map.of(
                "txnid", "txn123",
                "hash", "realHash"
        );

        String result = paymentService.verifyPayment(params);
        assertEquals("Hash mismatch! Payment verification failed.", result);
    }

    @Test
    void shouldReturnSuccessForIdempotentPayment() {
        payment.setPaymentStatus(PaymentStatus.SUCCESS);
        when(paymentRepository.findByGatewayOrderIdWithLock("txn123")).thenReturn(Optional.of(payment));

        Map<String, String> params = Map.of("txnid", "txn123", "hash", "hash123");
        when(payUServiceImp.generatePayuResponseHash(params)).thenReturn("hash123");

        String result = paymentService.verifyPayment(params);
        assertEquals("Success", result);
    }

    @Test
    void shouldConfirmAppointmentOnSuccessfulPayment() {
        when(paymentRepository.findByGatewayOrderIdWithLock("txn123")).thenReturn(Optional.of(payment));
        when(payUServiceImp.generatePayuResponseHash(any())).thenReturn("hash123");

        Map<String, String> params = Map.of(
                "txnid", "txn123",
                "mihpayid", "mih123",
                "status", "success",
                "hash", "hash123",
                "amount", "500"
        );

        String result = paymentService.verifyPayment(params);

        assertEquals("Success", result);
        assertEquals(PaymentStatus.SUCCESS, payment.getPaymentStatus());
        assertEquals(AppointmentStatus.CONFIRMED, appointment.getAppointmentStatus());
        verify(publisher).publishBooked(any());
        verify(paymentRepository).save(payment);
        verify(appointmentRepository).save(appointment);
    }

    @Test
    void shouldReturnFailureOnFailedPayment() {
        when(paymentRepository.findByGatewayOrderIdWithLock("txn123")).thenReturn(Optional.of(payment));
        when(payUServiceImp.generatePayuResponseHash(any())).thenReturn("hash123");

        Map<String, String> params = Map.of(
                "txnid", "txn123",
                "mihpayid", "mih123",
                "status", "failure",
                "hash", "hash123",
                "amount", "500"
        );

        String result = paymentService.verifyPayment(params);

        assertEquals("Failure", result);
        assertEquals(PaymentStatus.FAILED, payment.getPaymentStatus());
        verify(paymentRepository).save(payment);
        verifyNoInteractions(appointmentRepository, publisher);
    }
}

