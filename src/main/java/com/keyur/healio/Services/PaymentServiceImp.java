package com.keyur.healio.Services;

import com.keyur.healio.CustomExceptions.InvalidOperationException;
import com.keyur.healio.CustomExceptions.ResourceNotFoundException;
import com.keyur.healio.DTOs.AppointmentEventDto;
import com.keyur.healio.DTOs.PaymentOrderResponseDto;
import com.keyur.healio.Entities.Appointment;
import com.keyur.healio.Entities.Payment;
import com.keyur.healio.Entities.User;
import com.keyur.healio.Enums.AppointmentEventType;
import com.keyur.healio.Enums.AppointmentStatus;
import com.keyur.healio.Enums.PaymentStatus;
import com.keyur.healio.Repositories.AppointmentRepository;
import com.keyur.healio.Repositories.PaymentRepository;
import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;


import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class PaymentServiceImp implements PaymentService {
    //values
    @Value("${payu.merchant.key}")
    private String merchantKey;

    @Value("${payu.base.url}")
    private String payuBaseUrl;

    @Value("${payu.merchant.salt}")
    private String merchantSalt;

    //fields
    private final AppointmentRepository appointmentRepository;
    private final PaymentRepository paymentRepository;
    private final StudentServiceImp studentServiceImp;
    private final PayUServiceImp payUServiceImp;
    private final AppointmentEventPublisher publisher;

    public PaymentServiceImp(AppointmentRepository appointmentRepository,
                             PaymentRepository paymentRepository,
                             StudentServiceImp studentServiceImp,
                             PayUServiceImp payUServiceImp,
                             AppointmentEventPublisher publisher) {
        this.appointmentRepository = appointmentRepository;
        this.paymentRepository = paymentRepository;
        this.studentServiceImp = studentServiceImp;
        this.payUServiceImp = payUServiceImp;
        this.publisher = publisher;
    }


    //method to create a razorpay order for payment
    @Override
    @Transactional
    public PaymentOrderResponseDto createOrder(int appointmentId) {
        //fetch the appointment from database and check if it is valid or not
        Appointment appointmentForPayment = appointmentRepository.findByIdWithLock(appointmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Invalid appointment!"));

        //check if the appointment status is already succeeded or not.
        if(appointmentForPayment.getAppointmentStatus().equals(AppointmentStatus.CONFIRMED)) {
            throw new InvalidOperationException("Payment for this appointment is already processed!");
        }

        //check if the student who booked the appointment is only the one making the payment
        User currentUser = studentServiceImp.getCurrentUser();
        if (appointmentForPayment.getStudent().getId() != currentUser.getId()) {
            throw new InvalidOperationException("You cannot pay for someone else's appointment");
        }

        //check if the appointment time is expired
        if(appointmentForPayment.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new InvalidOperationException("Time for payment expired. Please try again!");
        }

        //check if the appointment is already cancelled
        if(appointmentForPayment.getAppointmentStatus().equals(AppointmentStatus.CANCELLED_STUDENT)
                || appointmentForPayment.getAppointmentStatus().equals(AppointmentStatus.CANCELLED_COUNSELLOR)) {
            throw new InvalidOperationException("The appointment is already cancelled");
        }

        //handle idempotency for multiple payment tries from the user
        Optional<Payment> existing = paymentRepository.findPendingPaymentByAppointmentId(appointmentId);
        if (existing.isPresent()) {
            // optionally return the same orderId instead of creating new
            return payUServiceImp.convertToResponse(existing.get(), currentUser);
        }

        String txnId = UUID.randomUUID().toString().replace("-", "").substring(0, 20);

        Integer amount = appointmentForPayment.getSlot().getPrice();

        // 4. Save Payment in DB
        Payment payment = new Payment();
        payment.setAppointment(appointmentForPayment);
        payment.setAmount(amount);
        payment.setPaymentStatus(PaymentStatus.PENDING);
        payment.setGatewayOrderId(txnId);
        payment.setCreatedAt(LocalDateTime.now());
        paymentRepository.save(payment);

        return payUServiceImp.createPaymentResponse(payment, currentUser);
    }

    //method to verify payment after the transaction is done
    //this method will redirect the user to a blank page currently with an appropriate message
    //appropriate redirect url to the frontend page has to be added
    @Override
    @Transactional
    public String verifyPayment(Map<String, String> params) {

        String txnId = params.get("txnid");
        String mihpayid = params.get("mihpayid");
        String status = params.get("status");
        String receivedHash = params.get("hash");


        Payment payment = paymentRepository
                .findByGatewayOrderIdWithLock(txnId)
                .orElseThrow(() -> new RuntimeException("Payment not found"));

        //hash verification (always first)
        String recalculatedHash = payUServiceImp.generatePayuResponseHash(params);
        System.out.println(recalculatedHash);
        if(!recalculatedHash.equals(receivedHash)) {
            return "Hash mismatch! Payment verification failed.";
        }

        //idempotency check
        if(payment.getPaymentStatus() == PaymentStatus.SUCCESS) {
            return "Success";
        }

        if(payment.getPaymentStatus() != PaymentStatus.PENDING) {
            throw new InvalidOperationException("Cannot process tampered payment!");
        }

        if(!payment.getGatewayOrderId().equals(txnId)) {
            throw new InvalidOperationException("Cannot process tampered payment!");
        }

        Appointment appointment = payment.getAppointment();

        //check if the verification is happening after the 10 cancellation window
        if(appointment.getAppointmentStatus().equals(AppointmentStatus.CANCELLED_EXTERNAL)) {
            throw new InvalidOperationException("Time for payment expired!");
        }

        BigDecimal receivedAmount = new BigDecimal(params.get("amount"));
        BigDecimal dbAmount = BigDecimal.valueOf(payment.getAmount());

        if (dbAmount.compareTo(receivedAmount) != 0) {
            throw new InvalidOperationException("Amount mismatch");
        }

        //update payment
        payment.setGatewayPaymentId(mihpayid);
        boolean isSuccess = "success".equalsIgnoreCase(status);
        payment.setPaymentStatus(isSuccess ? PaymentStatus.SUCCESS : PaymentStatus.FAILED);
        paymentRepository.save(payment);

        //post-payment business logic
        if (isSuccess) {
            appointment.setAppointmentStatus(AppointmentStatus.CONFIRMED);
            appointmentRepository.save(appointment);

            AppointmentEventDto event = new AppointmentEventDto();
            event.setAppointmentId(appointment.getId());
            event.setAppointmentTime(appointment.getAppointmentTime());
            event.setCounsellorEmail(appointment.getCounsellor().getEmail());
            event.setStudentEmail(appointment.getStudent().getEmail());
            event.setEventType(AppointmentEventType.BOOKED);

            publisher.publishBooked(event);
            return "Success";
        }

        return "Failure";
    }

}
