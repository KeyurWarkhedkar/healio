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
    public String verifyPayment(Map<String, String> params, boolean success) {
        String txnId = params.get("txnid");
        String mihpayid = params.get("mihpayid");
        String status = params.get("status");
        String receivedHash = params.get("hash");

        // Fetch payment from DB
        Payment payment = paymentRepository.findByGatewayOrderId(txnId)
                .orElseThrow(() -> new RuntimeException("Payment not found"));

        //handle idempotency (no need for lock here as PayU calls the api again after a lot of time.
        //so it would not cause any race condition. further two concurrent transaction at this stage
        //will only update the status to SUCCESS two times, so lost update is not a problem as well)
        if(payment.getPaymentStatus().equals(PaymentStatus.SUCCESS)) {
            return "Success";
        }

        // Recalculate hash on backend
        String recalculatedHash = payUServiceImp.generatePayuResponseHash(params);

        if (!recalculatedHash.equals(receivedHash)) {
            return "Hash mismatch! Payment verification failed.";
        }

        // Update payment status
        payment.setGatewayPaymentId(mihpayid);
        payment.setPaymentStatus(status.equalsIgnoreCase("success") ? PaymentStatus.SUCCESS : PaymentStatus.FAILED);
        paymentRepository.save(payment);

        if(success) {
            Appointment newAppointment = payment.getAppointment();

            //set appointment status as confirmed
            newAppointment.setAppointmentStatus(AppointmentStatus.CONFIRMED);
            appointmentRepository.save(newAppointment);

            //create the appointment event and send notification
            AppointmentEventDto event = new AppointmentEventDto();
            event.setAppointmentId(newAppointment.getId());
            event.setAppointmentTime(newAppointment.getAppointmentTime());
            event.setCounsellorEmail(newAppointment.getCounsellor().getEmail());
            event.setStudentEmail(newAppointment.getStudent().getEmail());
            event.setEventType(AppointmentEventType.BOOKED);

            publisher.publishBooked(event);

            return "Success";
        } else {
            return "Failure";
        }
    }
}
